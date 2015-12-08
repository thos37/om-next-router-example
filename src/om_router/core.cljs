(ns om-router.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [bidi.bidi :as bidi :refer [match-route]]
            [om.dom :as dom]
            [pushy.core :as pushy]
            [datascript.core :as d])
  (:require-macros
            [cljs-log.core :refer [debug info warn severe]]))

(enable-console-print!)

;;; app state

(defonce conn
  (d/create-conn
    {:db/ident {:db/unique :db.unique/identity}
     :pg/id    {:db/unique :db.unique/identity}}))

(defn initialize-db []
  (d/transact! conn
    [{:db/id -16 :dt/type :dt/page :pg/id :index :pg/title "Welcome to Take 2" :pg/subtitle "Take 2 minutes to change your self. You're the only one who will."}
     {:db/id -17 :dt/type :dt/page :pg/id :create :pg/title "Create a Campaign" :pg/subtitle "Be the change we wish to see in the world."}
     {:db/id -18 :dt/type :dt/page :pg/id :join :pg/title "Join a Movement" :pg/subtitle "Create a new world."}
     {:db/id -19 :dt/type :dt/page :pg/id :login :pg/title "Login" :pg/subtitle "One step away..."}
     {:db/id -20 :dt/type :dt/page :pg/id :unknown :pg/title "Careful, ninja, this way is the unknown ..."}
     {:db/ident :router :route :index}]))

;;; routing

(def routes ["/" {"" :index
                  "create" :create
                  "join" :join
                  "login" :login}])

(def route->url (partial bidi/path-for routes))

(defn parse-url [url]
  (match-route routes url))

;;; components

(defui Page
  static om/IQuery
  (query [this]
    [:pg/title :pg/subtitle])
  Object
  (render [this]
    (let [{:keys [:pg/title :pg/subtitle]} (om/props this)]
      (dom/div nil
        (dom/h1 nil title)
        (dom/div nil subtitle)))))

(defui OtherPage
  static om/IQuery
  (query [this]
    [:pg/title :pg/subtitle])
  Object
  (render [this]
    (let [{:keys [:pg/title :pg/subtitle]} (om/props this)]
      (dom/div nil
        (dom/h1 nil title)
        (dom/div nil subtitle)))))

(defn menu-entry
  [{:keys [route selected?] :as props}]
  (dom/li nil
    (dom/a (clj->js {:href (route->url route)})
      (if selected? (str (name route) " (selected)") (name route)))))

;;; Mapping from route to components, queries, and factories

(def route->component
  {:index Page
   :create Page
   :join OtherPage
   :login OtherPage})

(def route->factory
  (zipmap (keys route->component)
          (map om/factory (vals route->component))))

(def route->query
  (zipmap (keys route->component)
          (map om/get-query (vals route->component))))

;;; parsing

(defmulti mutate om/dispatch)

(defn set-route [conn route]
  (let [tx [{:db/id [:db/ident :router] :route route}]]
    (debug "set-route" route tx)
    (d/transact! conn tx)))

(defmethod mutate 'route/update
  [{:keys [state]} _ params]
  (let [_ (debug "MUTATE route/update" params)]
    {:action (fn [] (set-route state (:new-route params)))}))

(defmulti read om/dispatch)

(defn get-route [db]
  (let [ent (d/pull db '[:route] [:db/ident :router])
        route (:route ent)]
    (debug "get-route" route)
    route))

(defn get-page [db route]
  (let [query (route->query route)
        page (d/pull db query [:pg/id route])]

    (debug "get-page" query page)
    page))

(defmethod read :route
  [{:keys [state query]} _ params]
  (let [db @state
        route (get-route db)
        page (get-page db route)
        route {route page}
        _ (debug "READ :route" route)]
    {:value route}))

(declare history)

; (defn update-query-from-route!
;   [this route]
;   (debug "Set-Params!")
;   (om/set-query! this {:params {:page (get route->query route)}}))

(defui Root
  static om/IQuery
  (query [this]
    [:route])

  Object
  (nav-handler
    [this match]
    "Sync: Browser -> OM"
    (debug "Pushy caught a nav change" match)
    (let [{route :handler} match]
      ;(update-query-from-route! this route)
      ;[(app/set-page! ~match) :page]
      (om/transact! this `[(route/update {:new-route ~route})])))

  ; (componentWillReceiveProps
  ;   [this props]
  ;   "Sync: OM -> Browser"
  ;   (let [[old-route new-route] [(:route (om/props this)) (:route props)]]
  ;     (debug "componentWillReceiveProps" old-route new-route)
  ;     (when (not= new-route old-route)
  ;       ;(update-query-from-route! this new-route)
  ;       (pushy/set-token! history (or (route->url new-route) "/unknown-route")))))

  (componentDidMount [this]
    (debug "App mounted")
    (let [nav-fn #(.nav-handler this %)
          pushy (pushy/pushy nav-fn parse-url)]
      (debug "HISTORY/get-token" (pushy/get-token pushy))
      (pushy/start! pushy)
      (set! history pushy)))

  (componentWillUnmount [this]
    (debug "App unmounting")
    (pushy/stop! history))

  (render [this]
    (let [{:keys [route]} (om/props this)
          route-key (key (first route))
          page-data (route-key route)
          entries (vals (second routes))]

      (debug "RENDERING" route-key)

      (dom/div nil
        ;; routing via pushy
        (dom/div nil
          (dom/p nil "change route via pushy and anchor tags")
          (apply dom/ul nil
                 (map (fn [cur-route]
                        (menu-entry {:route cur-route :selected? (= cur-route route-key)}))
                      entries)))

        ;; routing in a transaction
        (dom/div nil
          (dom/p nil "change route via a transaction")
          (dom/button
            #js {:onClick
                 #(om/transact! this
                    '[(route/update {:new-route :login})])}
            "goto login"))

        ;; render the current page here
        (dom/div nil
          (dom/p nil "current page:")
          ((route->factory route-key) page-data))))))

(def parser (om/parser {:read read :mutate mutate}))

(initialize-db)

(def reconciler (om/reconciler {:state conn :parser parser}))

(om/add-root! reconciler Root (gdom/getElement "app"))

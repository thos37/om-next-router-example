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
     :pg/id {:db/unique :db.unique/identity}}))

(defn initialize-db []
  (d/transact! conn
    [{:db/id -1
      :dt/type :dt/campaign
      :campaign/name "Support Take 2"
      :campaign/description "Take 2 minutes for a take 2 on democracy."
      :campaign/location "Nevada City, CA"
      :campaign/goal 5000
      :campaign/committed 1000
      :campaign/started (js/Date. "2015-01-01T00:00:00-0800")
      :campaign/ends (js/Date. "2016-01-01T00:00:00-0800")
      }
     {:db/id -5
      :dt/type :dt/campaign
      :campaign/name "Net Neutrality"
      :campaign/description "Keep the internet free."
      :campaign/location "New York, NY"
      :campaign/goal 10000
      :campaign/committed 1000
      :campaign/started (js/Date. "2015-01-01T00:00:00-0800")
      :campaign/ends (js/Date. "2016-01-01T00:00:00-0800")
      }
     {:db/id -6
      :dt/type :dt/campaign
      :campaign/name "Money out of Politics"
      :campaign/description "Free speech comes from real human mouths."
      :campaign/location "Washington, DC"
      :campaign/goal 50000
      :campaign/committed 1000
      :campaign/started (js/Date. "2015-01-01T00:00:00-0800")
      :campaign/ends (js/Date. "2016-01-01T00:00:00-0800")
      }
     {:db/id -7
      :dt/type :dt/campaign
      :campaign/name "Climate Change"
      :campaign/description "What can we change about how we impact our Earth?"
      :campaign/location "Oakland, CA"
      :campaign/goal 100000
      :campaign/committed 1000
      :campaign/started (js/Date. "2015-01-01T00:00:00-0800")
      :campaign/ends (js/Date. "2016-01-01T00:00:00-0800")
      }
     {:db/id -12
      :dt/type :dt/campaign
      :campaign/name "Political Engagement"
      :campaign/description "Inspire! Organize! Succeed!"
      :campaign/location "Sacramento, CA"
      :campaign/goal 50000
      :campaign/committed 1000
      :campaign/started (js/Date. "2015-01-01T00:00:00-0800")
      :campaign/ends (js/Date. "2016-01-01T00:00:00-0800")
      }
     {:db/id -16 :dt/type :dt/page :pg/id :index :pg/title "Welcome to Take 2" :pg/subtitle "Take 2 minutes to change your self. You're the only one who will."}
     {:db/id -17 :dt/type :dt/page :pg/id :create :pg/title "Create a Campaign" :pg/subtitle "Be the change we wish to see in the world."}
     {:db/id -18 :dt/type :dt/page :pg/id :join :pg/title "Join a Movement" :pg/subtitle "Create a new world."}
     {:db/id -19 :dt/type :dt/page :pg/id :login :pg/title "Login" :pg/subtitle "One step away..."}
     {:db/id -20 :dt/type :dt/page :pg/id :unknown :pg/title "Careful, ninja, this way is the unknown ..."}
     {:db/id -21 :dt/type :dt/page :pg/id :list :pg/title "Campaigns"}
     {:db/id -22 :dt/type :dt/page :pg/id :detail :pg/title "Campaign Detail"}
     {:db/ident :campaign/list :dt/type :dt/list :list/type :dt/campaign}
     {:db/ident :router :route :index}]))

;;; routing

(def routes ["/" {"" :index
                  "create" :create
                  "join" :join
                  "login" :login
                  "list" :list
                  "detail" :detail}])

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
      (debug "RENDER Page")
      (dom/div nil
        (dom/div nil "Page")
        (dom/h1 nil title)
        (dom/div nil subtitle)))))

(defui OtherPage
  static om/IQuery
  (query [this]
    [:pg/title :pg/subtitle])
  Object
  (render [this]
    (let [{:keys [:pg/title :pg/subtitle]} (om/props this)]
      (debug "RENDER OtherPage")
      (dom/div nil
        (dom/div nil "Other Page")
        (dom/h1 nil title)
        (dom/div nil subtitle)))))

(defui ListPage
  static om/IQuery
  (query [this]
    [:pg/title {:campaign/list [:db/id :campaign/name]}])
  Object
  (render [this]
    (let [{:keys [:pg/title :campaign/list]} (om/props this)]
      (debug "RENDER ListPage")
      (dom/div nil "ListPage"
        (dom/h1 nil title)
        (dom/ul #js {:className "thumbnails list-unstyled"}
          (for [c list]
            (dom/li nil
              (dom/a #js{:href (str (route->url :detail) "/" (:db/id c))} (:campaign/name c)))))))))

(defui DetailPage
  static om/IQuery
  (query [this]
    [:db/id :campaign/name :campaign/description])
  Object
  (render [this]
    (let [{:keys [:campaign/name :campaign/description]} (om/props this)]
      (debug "RENDER DetailPage")
      (dom/div nil "DetailPage"
        (dom/h1 nil name)
        (dom/div nil description)))))

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
   :login OtherPage
   :list ListPage
   :detail DetailPage})

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

(defn get-route []
  (let [location js/window.location.pathname
        route (:handler (parse-url location))]
    route))

;(defn get-route [db]
;  (let [ent (d/pull db '[:route] [:db/ident :router])
;        route (:route ent)]
;    (debug "get-route" route)
;    route))

(defn get-page [db route query]
  (let [ ;query (route->query route)
        page (d/pull db query [:pg/id route])]

    (debug "get-page" query page)
    page))

(defmethod read :route
  [{:keys [state query ast]} _ params]
  (let [db @state
        _ (debug "READ :route query" query)
        _ (debug "READ :route ast" ast)
        ;route (get-route)
        route (key (first query))
        page-query (route query)
        page (get-page db route page-query)
        route {route page}
        _ (debug "READ :route" route)]
    {:value route}))

(defmethod read :default
  [{:keys [state query ast]} _ params]
  (let [db @state
        _ (debug "READ DEFAULT" "query" query "ast" ast)
        ;route (get-route db)
        ;page (get-page db route)
        ;route {route page}
        ;_ (debug "READ :route" route)
        ]
    {:value :unknown}))

(declare history)

(defui Root
  static om/IQuery
  (query [this]
    [{:route route->query}])

  Object

  ;(componentWillUnmount [this]
  ;  (debug "App unmounting")
  ;  (pushy/stop! history))

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
            #js {:onClick #(pushy/set-token! history (route->url :login))}
            "goto login"))

        ;; render the current page here
        (dom/div nil
          (dom/p nil "current page:")
          ((route->factory route-key) page-data))))))

(def parser (om/parser {:read read :mutate mutate}))

(initialize-db)

(def reconciler (om/reconciler {:state conn :parser parser}))

(defn nav-handler
  [match]
  "Sync: Browser -> OM"
  (debug "Pushy caught a nav change" match)
  (let [{new-route :handler} match
        old-route (get-route)
        _ (debug "route change" old-route new-route)
        new-query [{:route {new-route (new-route route->query)}}]
        _ (debug "new-query" new-query (om/query->ast new-query))
        ]
    (om/set-query! reconciler {:query new-query})
    ;(if-not (= old-route new-route)
    ;  ;(om/transact! reconciler `[(route/update {:new-route ~new-route})])
    ;
    ;  )
    ))

(let [location js/window.location.pathname
      route (:handler (parse-url location))
      pushy (pushy/pushy nav-handler parse-url)]
  (debug "HISTORY/get-token" (pushy/get-token pushy))
  (debug "location:route" location route)
  (pushy/start! pushy)
  (set! history pushy)
  ;(set-route conn route)
  )


(om/add-root! reconciler Root (gdom/getElement "app"))

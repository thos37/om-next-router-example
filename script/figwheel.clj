(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
 {:figwheel-options {:server-port 3451}
  :build-ids ["dev"]
  :all-builds
  [{:id "dev"
    :figwheel true
    :source-paths ["src"]
    :compiler {:main 'om-router.core
               :optimizations :none
               :asset-path "js"
               :output-to "resources/public/js/main.js"
               :output-dir "resources/public/js"
               :pseudo-names true
               :pretty-print true
               :verbose true}}]})

(ra/cljs-repl)

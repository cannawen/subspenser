(defproject spenser-server "0.1.0-SNAPSHOT"
  :description "HTTP server that appends timestamped measurements to a file"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.7.0"]
                 [ring/ring-core "1.11.0"]]
  :main spenser-server.core
  :aot [spenser-server.core])

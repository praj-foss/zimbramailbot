(defproject in.praj.zimbramailbot/chat "0.0.1-SNAPSHOT"
  :description "Chat service of Zimbra Mailbot"
  :url "https://github.com/praj-foss/zimbramailbot"

  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.8.1"]
                 [compojure "1.6.1"]
                 [clj-http "3.10.1"]]
  :plugins [[lein-ring "0.12.5"]]
  :main    chat.app
  :ring    {:handler chat.app/handler}

  :profiles  {:dev {:dependencies [[ring/ring-mock "0.3.2"]
                                   [se.haleby/stub-http "0.2.8"]]}
              :uberjar {:aot :all}})

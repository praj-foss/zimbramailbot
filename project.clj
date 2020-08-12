(defproject in.praj/zimbramailbot "0.0.5-SNAPSHOT"
  :description "Telegram bot for Zimbra mail server"
  :url "https://github.com/praj-foss/zimbramailbot"

  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure    "1.10.1"]
                 [org.clojure/core.async "1.3.610"]
                 [ring/ring-core         "1.8.1"]
                 [ring/ring-defaults     "0.3.2"]
                 [compojure              "1.6.1"]
                 [http-kit               "2.4.0"]
                 [cheshire               "5.10.0"]
                 [environ                "1.2.0"]]
  :plugins [[lein-environ "1.2.0"]]
  :main zimbramailbot.app

  :profiles
  {:dev  {:dependencies [[ring/ring-devel "1.8.1"]
                         [ring/ring-mock  "0.4.0"]]
          :env  {:environment "development"}}

   :prod {:env  {:environment "production"}
          :aot  :all}

   :uberjar {:aot :all}})

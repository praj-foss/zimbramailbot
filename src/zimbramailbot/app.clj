(ns zimbramailbot.app
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.client :as http]
            [org.httpkit.server :refer [run-server]]
            [cheshire.core :as json]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults
             :refer [wrap-defaults api-defaults]])
  (:gen-class))

(defn set-webhook [api-url hook-url]
  @(http/get (str api-url "/setWebhook")
             {:headers {"Content-Type" "application/json"}
              :body    (json/generate-string {:url hook-url})
              :as      :text}))

(defroutes app-routes
  (route/not-found "Not Found"))

(def handler
  (wrap-defaults app-routes api-defaults))

(defn -main [& args]
  (run-server (wrap-reload #'handler) {:port 8080}))

(ns chat.app
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn set-webhook [api-url hook-url]
  @(http/get (str api-url "/setWebhook")
             {:headers {"Content-Type" "application/json"}
              :body    (json/generate-string {:url hook-url})
              :as      :text}))

(defroutes app-routes
  (route/not-found "Not Found"))

(def handler
  (wrap-defaults app-routes site-defaults))

(ns zimbramailbot.app
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.client :as http]
            [org.httpkit.server :refer [run-server]]
            [cheshire.core :as json]
            [cidr.core :as cidr]
            [ring.util.response :as res]
            [ring.middleware.proxy-headers
             :refer [wrap-forwarded-remote-addr]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults
             :refer [wrap-defaults api-defaults]])
  (:gen-class))

(defn set-webhook [api-url hook-url]
  @(http/get (str api-url "/setWebhook")
             {:headers {"Content-Type" "application/json"}
              :body    (json/generate-string {:url hook-url})
              :as      :text}))

(defn parse-update [update]
  (let [update-map (json/parse-string update)
        user       (get-in update-map ["message" "from" "id"])
        command    (get-in update-map ["message" "text"])]
    {:user user
     :command (some-> (re-find #"^/[a-z]+$" command)
                      (subs 1)
                      (keyword))}))

(defn- ipv4? [addr]
  (-> (str "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}"
           "(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$")
      (re-pattern)
      (re-find addr)
      (some?)))

(defroutes app-routes
  (wrap-forwarded-remote-addr
   (POST "/updates" {ip :remote-addr}
         (if (and (ipv4? ip)
                  (or (cidr/in-range? ip "149.154.160.0/20")
                      (cidr/in-range? ip "91.108.4.0/22")))
           (res/status 200))))
  (route/not-found "Not Found"))

(def handler
  (wrap-defaults app-routes api-defaults))

(defn -main [& args]
  (run-server (wrap-reload #'handler) {:port 8080}))

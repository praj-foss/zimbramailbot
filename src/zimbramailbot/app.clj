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
             :refer [wrap-defaults api-defaults]]
            [clojure.core.async :as y])
  (:gen-class))

(defn set-webhook [api-url hook-url]
  @(http/post (str api-url "/setWebhook")
              {:headers {"Content-Type" "application/json"}
               :body    (json/generate-string
                         {"url" hook-url "allowed_updates" ["message"]})}))

(defn parse-update [umap]
  (let [chat       (get-in umap ["message" "chat" "id"])
        command    (get-in umap ["message" "text"])]
    {:chat chat
     :command (some-> (re-find #"^/[a-z]+$" command)
                      (subs 1)
                      (keyword))}))

(def sessions (atom {}))

(defmulti process-update #(:command %))

(defmethod process-update :start
  [umap]
  (->> (str "Hello! I'm Zimbra Mailbot.\n"
            "I can forward emails from your "
            "Zimbra mailbox to this chat. "
            "To log into your account, send "
            "/login command. Send /help to "
            "list all available commands.")
       (assoc umap :reply)))

(defmethod process-update :help
  [umap]
  (->> (str "I can understand these commands:\n"
            "/login - get link to log into my service\n"
            "/logout - log out of my service\n"
            "/help - display this help message again")
       (assoc umap :reply)))

(defmethod process-update :login
  [{chat :chat :as umap}]
  (->> (if-not (contains? @sessions chat)
         (do (swap! sessions assoc chat {})
             "Logged in successfully!")
         "You're logged in already.")
       (assoc umap :reply)))

(defmethod process-update :logout
  [{chat :chat :as umap}]
  (->> (if (contains? @sessions chat)
         (do (swap! sessions dissoc chat)
             "Logged out successfully!")
         "You're logged out already.")
       (assoc umap :reply)))

(defmethod process-update :default
  [umap]
  (->> (str "Unrecognized command. Send /help "
            "to list all available commands.")
       (assoc umap :reply)))

(defn send-message [api-url {chat :chat reply :reply}]
  @(http/post (str api-url "/sendMessage")
              {:headers {"Content-Type" "application/json"}
               :body    (json/generate-string
                         {"chat_id" chat "text" reply})}))

(def ^:private updates-chan (y/chan 32))

(defn processor-chan [in-chan size]
  (let [out-chan (y/chan size)]
    (y/go-loop []
      (when-some [u (y/<! in-chan)]
        (y/>! out-chan (-> (parse-update u)
                           (process-update)))
        (recur)))
    out-chan))

(defn- ipv4? [addr]
  (-> (str "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}"
           "(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$")
      (re-pattern)
      (re-find addr)
      (some?)))

(def updates-route
  (wrap-forwarded-remote-addr
   (POST "/updates" {ip :remote-addr body :body}
         (if (and (ipv4? ip)
                  (or (cidr/in-range? ip "149.154.160.0/20")
                      (cidr/in-range? ip "91.108.4.0/22")))
           (y/alt!!
             [[updates-chan body]] (res/status 200)
             (y/timeout 2000)      (-> (res/status 503)
                                       (res/header "Retry-After" 60)))))))

(defroutes app-routes
  updates-route
  (route/not-found "Not Found"))

(def handler
  (wrap-defaults app-routes api-defaults))

(defn -main [& args]
  (run-server (wrap-reload #'handler) {:port 8080}))

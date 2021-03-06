(ns zimbramailbot.app
  (:require [compojure.core :refer :all]
            [compojure.route :refer [not-found]]
            [org.httpkit.client :as http]
            [org.httpkit.server :refer [run-server]]
            [cheshire.core :as json]
            [ring.util.response :as res]
            [ring.middleware.defaults
             :refer [wrap-defaults api-defaults]]
            [clojure.core.async :as y]
            [environ.core :refer [env]])
  (:gen-class))

(defn set-webhook [api-url hook-url]
  @(http/post (str api-url "/setWebhook")
              {:headers {"Content-Type" "application/json"}
               :body    (json/generate-string
                         {"url" hook-url "allowed_updates" ["message"]})}))

(defn parse-update [body]
  (let [upd     (json/parse-string body)
        chat    (get-in upd ["message" "chat" "id"])
        command (get-in upd ["message" "text"])]
    {:chat chat
     :command (some-> (re-find #"^/[a-z]+$" command)
                      (subs 1)
                      (keyword))}))

(def ^:private sessions (atom {}))

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
      (if-some [u (y/<! in-chan)]
        (do (y/>! out-chan (-> (parse-update u)
                               (process-update)))
            (recur))
        (y/close! out-chan)))
    out-chan))

(defn sender-chan [in-chan api-url]
  (y/go-loop []
    (when-some [r (y/<! in-chan)]
      (send-message api-url r)
      (recur))))

(def main-route
  (GET "/" []
       (-> (str "<a href=\"https://t.me/zimbramailbot\">"
                "@zimbramailbot</a>")
           (res/response)
           (res/content-type "text/html"))))

(defn updates-route [token]
  (POST (str "/" token) {body :body}
        (y/alt!!
          [[updates-chan (slurp body)]] (res/status 200)
          (y/timeout 2000) (-> (res/status 503)
                               (res/header "Retry-After" 60)))))

(defn handler [token]
  (-> (routes main-route
              (updates-route token)
              (not-found "Not found"))
      (wrap-defaults api-defaults)))

(def ^:private server-stopper (atom nil))

(defn start-server [handler port]
  (if-not @server-stopper
    (reset! server-stopper (run-server handler {:port port}))))

(defn stop-server [timeout]
  (when @server-stopper
    (@server-stopper :timeout timeout)
    (reset! server-stopper nil)))

(def ^:private config-keys
  #{:token :domain :port})

(defn read-config []
  (reduce #(assoc %1 %2 (env %2)) {} config-keys))

(defn validate-config [config]
  (let [valid? (fn [[k v]]
                 (and (config-keys k) (some? v)))]
    (if (and (not-empty config)
             (every? valid? config))
      config)))

(defn -main [& args]
  (if-let [conf (validate-config (read-config))]
    (let [token    (:token conf)
          api-url  (str "https://api.telegram.org/bot" token)
          hook-url (str (:domain conf) "/" token)
          port     (Integer/parseInt (:port conf))]
      (do (set-webhook api-url hook-url)
          (-> updates-chan
              (processor-chan 32)
              (sender-chan api-url))
          (start-server (handler token) port)
          (println "Server started on port" port)))
    (println "Invalid config: Failed to start server")))

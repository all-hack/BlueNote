(ns blue-note.core.handler
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as ring]
            [ring.middleware.json :as middleware]
            [clojure.java.jdbc :as sql]
            [blue-note.model.messages :as messages]
            [blue-note.model.beacons :as beacons]
            [blue-note.model.users :as users]
            [blue-note.model.receive-update :as receive-update]))

(defn- anon
  [message]
  (if (:anon message)
    (dissoc :from_user_id message)
    message))

(defn- getPublicMessages
  [beacon_id]
  (let [query "SELECT * FROM messages WHERE beacon = ? AND public = TRUE;"
        messages (sql/query messages/spec [query beacon_id])]
    (map #(anon %) messages)))

(defn- getMostRecent
  "Get's messages since last checked"
  [beacon_id user_id]
  (let[query2 "SELECT * FROM messages WHERE to_user_id = ? AND beacon = ? AND
       (time_posted > (SELECT last_update FROM receive_update WHERE user_id = ? AND beacon = ?));"]
    (sql/query messages/spec [query2 user_id beacon_id user_id beacon_id])))

(defn- getAll
  "current gets all direct messages a year back"
  [beacon_id user_id]
  (let [query2 "SELECT * FROM messages WHERE to_user_id = ? AND beacon = ? AND
        (time_posted > (NOW() - interval '1 year'));"]
    (sql/query messages/spec [query2 user_id beacon_id])))

(defn- updateLastChecked
  [beacon_id user_id]
  (let [query "UPDATE receive_update SET last_update = NOW() WHERE user_id = ? AND beacon = ?;"]
    (sql/execute! messages/spec [query user_id beacon_id])))

(defn- getMessageHelper
  [beacon_id user_id]
  (let [query1 "SELECT count(*) FROM receive_update WHERE user_id = ? AND beacon = ?;"
        hasChecked (-> (sql/query messages/spec [query1 user_id beacon_id])
                       first
                       :count
                       pos?)
        withNames
        (if false ;hasChecked
          (getMostRecent beacon_id user_id)
          (getAll beacon_id user_id))
        directMessages (map #(anon %) withNames)]
    (updateLastChecked beacon_id user_id)
      {:directMessages directMessages}))

(defn- getBeaconID
  [uuid major minor]
  (let [query "SELECT id FROM beacons WHERE uuid = ? AND major = ? AND minor = ?;"]
    (-> (sql/query messages/spec [query (str uuid) (read-string major) (read-string minor)])
        first
        :id)))

(defn- getMessages
  "checks to see if beacon is known, if it is, it looks for messages that have user id and the beacon
  id returned that were posted after last updated"
  [beacons user_id]
  (let [beacon_ids (map #(getBeaconID (str (:uuid %)) (:major %) (:minor %)) beacons)]
    ;; beacon is registered
    (when-not (empty? beacon_ids)
      ;; update last time read
      (map #(getMessageHelper % user_id) beacon_ids))))


;;;;;;

(defn- postMessage
  [toPost]
  (let [beacon (read-string (:beacon_id toPost))
        from_user_id (read-string (:from_user_id toPost))
        anon (:anon toPost)
        message (:message toPost)
        public true
        query "INSERT INTO message (beacon, from_user_id, anon, message, public) VALUES (?, ?, ?, ?, ?)"]
    (sql/execute! messages/spec [query beacon from_user_id anon message public])))

;;;;;

(defroutes app-routes
  ;; check to see if user has beacons/is willing to accept anything
  ;; if has beacons, checks to see if beacon is the one currently being talked to
  ;; if public, grabs any outstanding messages for that users.
  (GET "/getMessages" [:as request]
       (let [req (get-in request [:params])
             beacons (:beacons req)]
         (println req)
         (println [beacons])
         (ring/response (getMessages [beacons] (read-string (:user_id req))))))
  (GET "/getPublic" [:as request]
       (let [beacons (get-in request [:params :beacons])
             beacon_ids (map #(getBeaconID (str (:uuid %)) (:major %) (:minor %)) beacons)]
         (println beacons)
         (ring/response (map #(getPublicMessages %) beacon_ids))))
  (GET "/getBeaconID" [:as request]
       (let [beacon (get-in request [:params])]
         (ring/response (getBeaconID (:uuid beacon) (:major beacon) (:minor beacon)))))
  (POST "/postMessage" [:as request]
        (let [message (get-in request [:params])]
          (postMessage message)
          (ring/response {:status "success"})))
  (route/resources "/")
  ; if not found
  (route/not-found "Page not found"))

(def app
  "middlewear for HTTP
  from http://zaiste.net/2014/02/web_applications_in_clojure_all_the_way_with_compojure_and_om/"
  (-> (handler/api app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))

(defn -main [& [port]]
  (users/migrate)
  (beacons/migrate)
  (receive-update/migrate)
  (messages/migrate)
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (handler/site #'app) {:port port :join? false})))

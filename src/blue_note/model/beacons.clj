(ns blue-note.model.beacons
  (:require [clojure.java.jdbc :as sql]))

(def spec (or (System/getenv "DATABASE_URL")
              "postgres://epyqbvloacvrsx:ULrv2t3B_-Ee0J03SNXTJbpjDH@ec2-50-16-190-77.compute-1.amazonaws.com:5432/desa91a683m0c8?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"))

(defn migrated? []
  (-> (sql/query spec
                 [(str "select count(*) from information_schema.tables "
                       "where table_name='beacons';")])
      first :count pos?))

(defn migrate []
  (when (not (migrated?))
    (print "Creating database structure...") (flush)
    (sql/db-do-commands spec
                        (sql/create-table-ddl
                         :beacons
                         [:id :serial "PRIMARY KEY"] ;; could later be set by
                         [:uuid :varchar "NOT NULL"] ;; us to distinguish
                         [:major :int "NOT NULL" "DEFAULT 0"]
                         [:minor :int "NOT NULL" "DEFAULT 0"]
                         [:name :varchar "NOT NULL" "DEFAULT 'MYBEACON'"]
                         [:public :boolean "NOT NULL" "DEFAULT TRUE"]
                         [:owner :int "references users (id)" "NOT NULL" "DEFAULT 0"]))
    (println " done")))


;; phone will receive something from beacon, check if beacon is in DB, if it is, check messages
;; where db is this db and user id is userid and published later than last updated
;; otherwise this page is only used for registering new things and posting
;; if posting, you can't see it if it's private, if signing up, we need to know
;; if it's already registered.

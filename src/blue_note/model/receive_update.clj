(ns blue-note.model.receive-update
  (:require [clojure.java.jdbc :as sql]))

(def spec (or (System/getenv "DATABASE_URL")
              "postgres://epyqbvloacvrsx:ULrv2t3B_-Ee0J03SNXTJbpjDH@ec2-50-16-190-77.compute-1.amazonaws.com:5432/desa91a683m0c8?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"))

(defn migrated? []
  (-> (sql/query spec
                 [(str "select count(*) from information_schema.tables "
                       "where table_name='receive_update';")])
      first :count pos?))

(defn migrate []
  (when (not (migrated?))
    (print "Creating database structure...") (flush)
    (sql/db-do-commands spec
                        (sql/create-table-ddl
                         :receive_update
                         [:user_id :int "PRIMARY KEY" "references users (id)" "NOT NULL"]
                         [:beacon :int "references beacons (id)" "NOT NULL"]
                         [:last_update :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]))
    (println " done")))

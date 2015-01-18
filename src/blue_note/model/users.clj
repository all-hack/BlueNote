(ns blue-note.model.users
  (:require [clojure.java.jdbc :as sql]))

(def spec (or (System/getenv "DATABASE_URL")
              "postgres://epyqbvloacvrsx:ULrv2t3B_-Ee0J03SNXTJbpjDH@ec2-50-16-190-77.compute-1.amazonaws.com:5432/desa91a683m0c8?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"))

(defn migrated? []
  (-> (sql/query spec
                 [(str "select count(*) from information_schema.tables "
                       "where table_name='users'")])
      first :count pos?))

(defn migrate []
  (when (not (migrated?))
    (print "Creating database structure...") (flush)
    (sql/db-do-commands spec
                        (sql/create-table-ddl
                         :users
                         [:id :serial "PRIMARY KEY"]
                         [:username :varchar "NOT NULL"]
                         [:password :int "NOT NULL" "DEFAULT 1770155071"] ; "GUESTSALT" hashed
                         [:salt :varchar "NOT NULL" "DEFAULT 'SALT'"]
                         [:wants_public :boolean "NOT NULL" "DEFAULT TRUE"]))
    (println " done")))

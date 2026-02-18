(ns clojure-android.repl.server
  "nREPL server for Android development.

  Starts an nREPL server on the device that can be connected to from a
  development machine via adb port forwarding:
    adb forward tcp:7888 tcp:7888
    lein repl :connect 7888"
  (:require [nrepl.server :as nrepl]))

(defonce ^:private server (atom nil))

(defn start
  "Starts an nREPL server on the given port (default 7888).
  Returns the server instance. Idempotent — calling start when a server
  is already running returns the existing server."
  ([] (start 7888))
  ([port]
   (or @server
       (let [s (nrepl/start-server :port port)]
         (reset! server s)
         (android.util.Log/i "ClojureREPL"
                             (str "nREPL server started on port " port))
         s))))

(defn stop
  "Stops the running nREPL server, if any."
  []
  (when-let [s @server]
    (nrepl/stop-server s)
    (reset! server nil)
    (android.util.Log/i "ClojureREPL" "nREPL server stopped")))

(defn restart
  "Stops and restarts the nREPL server."
  ([] (restart 7888))
  ([port]
   (stop)
   (start port)))

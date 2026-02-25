(ns clj-android.repl.server
  "nREPL server for Android development.

  Starts an nREPL server on the device that can be connected to from a
  development machine via adb port forwarding:
    adb forward tcp:7888 tcp:7888
    lein repl :connect 7888"
  (:require [nrepl.server :as nrepl]))

(def ^:private ^String LOG_TAG "ClojureREPL")

(defonce ^:private server (atom nil))

(defn start
  "Starts an nREPL server on the given port (default 7888).
  Returns the server instance. Idempotent — calling start when a server
  is already running returns the existing server."
  ([] (start 7888))
  ([port]
   (if-let [existing @server]
     (do
       (android.util.Log/d LOG_TAG
                           (str "nREPL server already running: " existing))
       existing)
     (do
       (android.util.Log/i LOG_TAG
                           (str "Starting nREPL server on port " port "..."))
       (try
         (let [s (nrepl/start-server :port port)]
           (reset! server s)
           (android.util.Log/i LOG_TAG
                               (str "nREPL server started successfully on port "
                                    port " — server=" s))
           s)
         (catch Throwable t
           (android.util.Log/e LOG_TAG
                               (str "nREPL server failed to start on port "
                                    port ": " (.getName (class t))
                                    ": " (.getMessage t))
                               t)
           (throw t)))))))

(defn stop
  "Stops the running nREPL server, if any. Returns true if a server was
  stopped, false if none was running."
  []
  (if-let [s @server]
    (do
      (android.util.Log/i LOG_TAG (str "Stopping nREPL server: " s))
      (try
        (nrepl/stop-server s)
        (catch Throwable t
          (android.util.Log/w LOG_TAG "Error stopping nREPL server" t)))
      (reset! server nil)
      (android.util.Log/i LOG_TAG "nREPL server stopped")
      true)
    (do
      (android.util.Log/d LOG_TAG "stop called but no server running")
      false)))

(defn running?
  "Returns true if an nREPL server is currently running."
  []
  (some? @server))

(defn restart
  "Stops and restarts the nREPL server."
  ([] (restart 7888))
  ([port]
   (stop)
   (start port)))

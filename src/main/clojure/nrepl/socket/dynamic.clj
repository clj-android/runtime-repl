(ns nrepl.socket.dynamic
  "Android stub: socket-related code that depends on classes only known at run time.")

(set! *warn-on-reflection* false)

(defn get-path [addr] (.getPath addr))

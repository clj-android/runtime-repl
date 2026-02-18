(ns nrepl.socket
  "Android-compatible nREPL socket layer. Provides only TCP socket support
  since Unix domain sockets (java.net.UnixDomainSocketAddress) are not
  fully available on Android's ART runtime."
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io BufferedInputStream BufferedOutputStream OutputStream)
   (java.net InetSocketAddress ServerSocket Socket SocketAddress URI)
   (java.nio.channels ClosedChannelException ServerSocketChannel)))

;;; InetSockets (TCP) — the only type supported on Android

(defn inet-socket [bind port]
  (let [port (or port 0)
        addr (fn [^String bind port] (InetSocketAddress. bind (int port)))
        bind (or bind "127.0.0.1")]
    (doto (ServerSocket.)
      (.setReuseAddress true)
      (.bind (addr bind port)))))

;; Unix domain sockets — not available on Android
(def unix-domain-flavor nil)

(defn unix-socket-address [^String path]
  (throw (ex-info "Unix domain sockets are not supported on Android"
                  {:nrepl/kind ::no-filesystem-sockets})))

(defn unix-server-socket [^String path]
  (throw (ex-info "Unix domain sockets are not supported on Android"
                  {:nrepl/kind ::no-filesystem-sockets})))

(defn unix-client-socket [^String path]
  (throw (ex-info "Unix domain sockets are not supported on Android"
                  {:nrepl/kind ::no-filesystem-sockets})))

(defn as-nrepl-uri [sock transport-scheme]
  (let [sock ^ServerSocket sock]
    (URI. transport-scheme
          nil
          (-> sock .getInetAddress .getHostName)
          (.getLocalPort sock)
          nil nil nil)))

(defprotocol Acceptable
  (accept [s] "Accepts a connection on s."))

(extend-protocol Acceptable
  ServerSocketChannel
  (accept [s] (.accept s))

  ServerSocket
  (accept [s]
    (when (.isClosed s)
      (throw (ClosedChannelException.)))
    (.accept s)))

(defprotocol Writable
  (write
    [w byte-array]
    [w byte-array offset length]))

(extend-protocol Writable
  OutputStream
  (write
    ([s byte-array] (.write ^OutputStream s ^"[B" byte-array))
    ([s byte-array offset length]
     (.write ^OutputStream s byte-array offset length))))

(defprotocol AsBufferedInputStreamSubset
  (buffered-input [x]))

(extend-protocol AsBufferedInputStreamSubset
  Socket (buffered-input [s] (io/input-stream s))
  BufferedInputStream (buffered-input [s] s))

(defprotocol AsBufferedOutputStreamSubset
  (buffered-output [x]))

(extend-protocol AsBufferedOutputStreamSubset
  Socket (buffered-output [s] (io/output-stream s))
  BufferedOutputStream (buffered-output [s] s))

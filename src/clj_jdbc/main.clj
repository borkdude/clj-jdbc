(ns clj-jdbc.main
  {:no-doc true}
  (:require
   [clj-jdbc.impl.classes :as classes]
   [clj-jdbc.impl.classpath :as cp]
   [clj-jdbc.impl.clojure.core :refer [core-extras]]
   [clj-jdbc.impl.clojure.java.io :refer [io-namespace]]
   [clj-jdbc.impl.clojure.java.shell :refer [shell-namespace]]
   [clj-jdbc.impl.clojure.main :as clojure-main :refer [demunge]]
   [clj-jdbc.impl.clojure.pprint :refer [pprint-namespace]]
   [clj-jdbc.impl.clojure.stacktrace :refer [stacktrace-namespace]]
   [clj-jdbc.impl.common :as common]
   [clj-jdbc.impl.jdbc :as jdbc]
   [clj-jdbc.impl.nrepl-server :as nrepl-server]
   [clj-jdbc.impl.repl :as repl]
   [clj-jdbc.impl.sigint-handler :as sigint-handler]
   [clj-jdbc.impl.socket-repl :as socket-repl]
   [clj-jdbc.impl.test :as t]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]
   [sci.addons :as addons]
   [sci.core :as sci]
   [sci.impl.interpreter :refer [eval-string*]]
   [sci.impl.opts :as sci-opts]
   [sci.impl.unrestrict :refer [*unrestricted*]]
   [sci.impl.vars :as vars])
  (:gen-class))

(binding [*unrestricted* true]
  (sci/alter-var-root sci/in (constantly *in*))
  (sci/alter-var-root sci/out (constantly *out*))
  (sci/alter-var-root sci/err (constantly *err*)))

(set! *warn-on-reflection* true)
;; To detect problems when generating the image, run:
;; echo '1' | java -agentlib:native-image-agent=config-output-dir=/tmp -jar target/clj-jdbc-xxx-standalone.jar '...'
;; with the java provided by GraalVM.

(defn parse-opts [options]
  (let [opts (loop [options options
                    opts-map {}]
               (if options
                 (let [opt (first options)]
                   (case opt
                     ("--") (assoc opts-map :command-line-args (next options))
                     ("--version") {:version true}
                     ("--help" "-h" "-?") {:help? true}
                     ("--verbose")(recur (next options)
                                         (assoc opts-map
                                                :verbose? true))
                     ("--stream") (recur (next options)
                                         (assoc opts-map
                                                :stream? true))
                     ("--time") (recur (next options)
                                       (assoc opts-map
                                              :time? true))
                     ("-i") (recur (next options)
                                   (assoc opts-map
                                          :shell-in true))
                     ("-I") (recur (next options)
                                   (assoc opts-map
                                          :edn-in true))
                     ("-o") (recur (next options)
                                   (assoc opts-map
                                          :shell-out true))
                     ("-O") (recur (next options)
                                   (assoc opts-map
                                          :edn-out true))
                     ("-io") (recur (next options)
                                    (assoc opts-map
                                           :shell-in true
                                           :shell-out true))
                     ("-iO") (recur (next options)
                                    (assoc opts-map
                                           :shell-in true
                                           :edn-out true))
                     ("-Io") (recur (next options)
                                    (assoc opts-map
                                           :edn-in true
                                           :shell-out true))
                     ("-IO") (recur (next options)
                                    (assoc opts-map
                                           :edn-in true
                                           :edn-out true))
                     ("--classpath", "-cp")
                     (let [options (next options)]
                       (recur (next options)
                              (assoc opts-map :classpath (first options))))
                     ("--uberscript")
                     (let [options (next options)]
                       (recur (next options)
                              (assoc opts-map
                                     :uberscript (first options))))
                     ("-f" "--file")
                     (let [options (next options)]
                       (recur (next options)
                              (assoc opts-map
                                     :file (first options))))
                     ("--repl")
                     (let [options (next options)]
                       (recur (next options)
                              (assoc opts-map
                                     :repl true)))
                     ("--socket-repl")
                     (let [options (next options)
                           opt (first options)
                           opt (when-not (str/starts-with? opt "-")
                                 opt)
                           options (if opt (next options)
                                       options)]
                       (recur options
                              (assoc opts-map
                                     :socket-repl (or opt "1666"))))
                     ("--nrepl-server")
                     (let [options (next options)
                           opt (first options)
                           opt (when-not (str/starts-with? opt "-")
                                 opt)
                           options (if opt (next options)
                                       options)]
                       (recur options
                              (assoc opts-map
                                     :nrepl (or opt "1667"))))
                     ("--eval", "-e")
                     (let [options (next options)]
                       (recur (next options)
                              (update opts-map :expressions (fnil conj []) (first options))))
                     ("--main", "-m")
                     (let [options (next options)]
                       (recur (next options)
                              (assoc opts-map :main (first options))))
                     (if (some opts-map [:file :socket-repl :expressions :main])
                       (assoc opts-map
                              :command-line-args options)
                       (let [trimmed-opt (str/triml opt)
                             c (.charAt trimmed-opt 0)]
                         (case c
                           (\( \{ \[ \* \@ \#)
                           (-> opts-map
                               (update :expressions (fnil conj []) (first options))
                               (assoc :command-line-args (next options)))
                           (assoc opts-map
                                  :file opt
                                  :command-line-args (next options)))))))
                 opts-map))]
    opts))

(defn edn-seq*
  [^java.io.BufferedReader rdr]
  (let [edn-val (edn/read {:eof ::EOF} rdr)]
    (when (not (identical? ::EOF edn-val))
      (cons edn-val (lazy-seq (edn-seq* rdr))))))

(defn edn-seq
  [in]
  (edn-seq* in))

(defn shell-seq [in]
  (line-seq (java.io.BufferedReader. in)))

(defn print-version []
  (println (str "clj-jdbc v"(str/trim (slurp (io/resource "CLJ_JDBC_VERSION"))))))

(def usage-string "Usage: clj-jdbc [--verbose]
          [ ( --classpath | -cp ) <cp> ]
          [ ( --main | -m ) <main-namespace> | -e <expression> | -f <file> |
            --repl | --socket-repl [<host>:]<port> | --nrepl-server [<host>:]<port> ]
          [ arg* ]")
(defn print-usage []
  (println usage-string))

(defn print-help []
  (println (str "clj-jdbc v" (str/trim (slurp (io/resource "CLJ_JDBC_VERSION")))))
  (println)
  (print-usage)
  (println)
  (println "Options:")
  (println "
  --help, -h or -?    Print this help text.
  --version           Print the current version of clj-jdbc.
  --verbose           Print entire stacktrace in case of exception.

  -e, --eval <expr>   Evaluate an expression.
  -f, --file <path>   Evaluate a file.
  -cp, --classpath    Classpath to use.
  -m, --main <ns>     Call the -main function from namespace with args.
  --repl              Start REPL. Use rlwrap for history.
  --socket-repl       Start socket REPL. Specify port (e.g. 1666) or host and port separated by colon (e.g. 127.0.0.1:1666).
  --nrepl-server      Start nREPL server. Specify port (e.g. 1667) or host and port separated by colon (e.g. 127.0.0.1:1667).
  --                  Stop parsing args and pass everything after -- to *command-line-args*

If neither -e, -f, or --socket-repl are specified, then the first argument that is not parsed as a option is treated as a file if it exists, or as an expression otherwise.
Everything after that is bound to *command-line-args*."))

(defn read-file [file]
  (let [f (io/file file)]
    (if (.exists f)
      (as-> (slurp file) x
        ;; remove shebang
        (str/replace x #"^#!.*" ""))
      (throw (Exception. (str "File does not exist: " file))))))

(def reflection-var (sci/new-dynamic-var '*warn-on-reflection* false))

(defn load-file* [sci-ctx f]
  (let [f (io/file f)
        s (slurp f)]
    (sci/with-bindings {sci/ns @sci/ns
                        sci/file (.getCanonicalPath f)}
      (eval-string* sci-ctx s))))

(defn start-socket-repl! [address ctx]
  (socket-repl/start-repl! address ctx)
  ;; hang until SIGINT
  @(promise))

(defn start-nrepl! [address ctx]
  (nrepl-server/start-server! ctx address)
  ;; hang until SIGINT
  #_@(promise))

(defn exit [n]
  (throw (ex-info "" {:bb/exit-code n})))

(def aliases
  '{edn clojure.edn
    shell clojure.java.shell
    io clojure.java.io
    jdbc next.jdbc})

(def cp-state (atom nil))

(defn add-classpath* [add-to-cp]
  (swap! cp-state
         (fn [{:keys [:cp]}]
           (let [new-cp
                 (if-not cp add-to-cp
                         (str cp (System/getProperty "path.separator") add-to-cp))]
             {:loader (cp/loader new-cp)
              :cp new-cp})))
  nil)

(def namespaces
  { 'clojure.java.shell shell-namespace
   'clojure.java.io io-namespace
   'clojure.stacktrace stacktrace-namespace
   'clojure.main {'demunge demunge
                  'repl-requires clojure-main/repl-requires}
   'clojure.test t/clojure-test-namespace
   'clj-jdbc.classpath {'add-classpath add-classpath*}
   'clojure.pprint pprint-namespace
   'next.jdbc jdbc/njdbc-namespace})

(def bindings
  {'java.lang.System/exit exit ;; override exit, so we have more control
   'System/exit exit})

(defn error-handler* [^Exception e verbose?]
  (binding [*out* *err*]
    (let [d (ex-data e)
          exit-code (:bb/exit-code d)]
      (if exit-code [nil exit-code]
          (do (if verbose?
                (print-stack-trace e)
                (println (str (.. e getClass getName)
                              (when-let [m (.getMessage e)]
                                (str ": " m)) )))
              (flush)
              [nil 1])))))

(defn main
  [& args]
  (sigint-handler/handle-sigint!)
  #_(binding [*out* *err*]
      (prn "M" (meta (get bindings 'future))))
  (binding [*unrestricted* true]
    (sci/binding [reflection-var false
                  sci/ns (vars/->SciNamespace 'user nil)]
      (let [t0 (System/currentTimeMillis)
            {:keys [:version :shell-in :edn-in :shell-out :edn-out
                    :help? :file :command-line-args
                    :expressions :stream? :time?
                    :repl :socket-repl :nrepl
                    :verbose? :classpath
                    :main :uberscript] :as _opts}
            (parse-opts args)
            _ (when main (System/setProperty "clj-http.main" main))
            read-next (fn [*in*]
                        (if false
                          ::EOF
                          (if stream?
                            (if shell-in (or (read-line) ::EOF)
                                (edn/read {;;:readers *data-readers*
                                           :eof ::EOF} *in*))
                            (delay (cond shell-in
                                         (shell-seq *in*)
                                         edn-in
                                         (edn-seq *in*)
                                         :else
                                         (edn/read *in*))))))
            uberscript-sources (atom ())
            env (atom {})
            classpath (or classpath
                          (System/getenv "CLJ_JDBC_CLASSPATH"))
            _ (when classpath
                (add-classpath* classpath))
            load-fn (fn [{:keys [:namespace]}]
                      (when-let [{:keys [:loader]} @cp-state]
                        (let [res (cp/source-for-namespace loader namespace nil)]
                          (when uberscript (swap! uberscript-sources conj (:source res)))
                          res)))
            _ (when file (vars/bindRoot sci/file (.getCanonicalPath (io/file file))))
            ctx {:aliases aliases
                 :namespaces (-> namespaces
                                 (assoc 'clojure.core
                                        (assoc core-extras
                                               '*command-line-args*
                                               (sci/new-dynamic-var '*command-line-args* command-line-args)
                                               '*warn-on-reflection* reflection-var))
                                 (assoc-in ['clojure.java.io 'resource]
                                           #(when-let [{:keys [:loader]} @cp-state] (cp/getResource loader % {:url? true}))))
                 :bindings bindings
                 :env env
                 :features #{:bb :clj}
                 :classes classes/class-map
                 :imports '{ArithmeticException java.lang.ArithmeticException
                            AssertionError java.lang.AssertionError
                            BigDecimal java.math.BigDecimal
                            Boolean java.lang.Boolean
                            Byte java.lang.Byte
                            Class java.lang.Class
                            ClassNotFoundException java.lang.ClassNotFoundException
                            Double java.lang.Double
                            Exception java.lang.Exception
                            IllegalArgumentException java.lang.IllegalArgumentException
                            Integer java.lang.Integer
                            File java.io.File
                            Long java.lang.Long
                            Math java.lang.Math
                            NumberFormatException java.lang.NumberFormatException
                            Object java.lang.Object
                            Runtime java.lang.Runtime
                            RuntimeException java.lang.RuntimeException
                            ProcessBuilder java.lang.ProcessBuilder
                            String java.lang.String
                            StringBuilder java.lang.StringBuilder
                            System java.lang.System
                            Thread java.lang.Thread
                            Throwable java.lang.Throwable}
                 :load-fn load-fn
                 :dry-run uberscript}
            ctx (addons/future ctx)
            sci-ctx (sci-opts/init ctx)
            _ (vreset! common/ctx sci-ctx)
            input-var (sci/new-dynamic-var '*input* nil)
            _ (swap! (:env sci-ctx)
                     (fn [env]
                       (update env :namespaces
                               (fn [namespaces] [:namespaces 'clojure.main 'repl]
                                 (-> namespaces
                                     (assoc-in ['clojure.core 'load-file] #(load-file* sci-ctx %))
                                     (assoc-in ['clojure.main 'repl]
                                               (fn [& opts]
                                                 (let [opts (apply hash-map opts)]
                                                   (repl/start-repl! sci-ctx opts))))
                                     (assoc-in ['user (with-meta '*input*
                                                        (when-not stream?
                                                          {:sci.impl/deref! true}))] input-var))))))
            [expressions exit-code]
            (cond expressions [expressions nil]
                  main [[(format "(ns user (:require [%1$s])) (apply %1$s/-main *command-line-args*)"
                                 main)] nil]
                  file (try [[(read-file file)] nil]
                            (catch Exception e
                              (error-handler* e verbose?))))
            expression (str/join " " expressions) ;; this might mess with the locations...
            exit-code
            (or exit-code
                (second
                 (cond version
                       [(print-version) 0]
                       help?
                       [(print-help) 0]
                       repl [(repl/start-repl! sci-ctx) 0]
                       socket-repl [(start-socket-repl! socket-repl sci-ctx) 0]
                       nrepl [(start-nrepl! nrepl sci-ctx) 0]
                       (not (str/blank? expression))
                       (try
                         (loop []
                           (let [in (read-next *in*)]
                             (if (identical? ::EOF in)
                               [nil 0] ;; done streaming
                               (let [res [(let [res
                                                (sci/binding [input-var in]
                                                  (eval-string* sci-ctx expression))]
                                            (when (some? res)
                                              (if-let [pr-f (cond shell-out println
                                                                  edn-out prn)]
                                                (if (coll? res)
                                                  (doseq [l res
                                                          :while (not true)]
                                                    (pr-f l))
                                                  (pr-f res))
                                                (prn res)))) 0]]
                                 (if stream?
                                   (recur)
                                   res)))))
                         (catch Throwable e
                           (error-handler* e verbose?)))
                       uberscript [nil 0]
                       :else [(repl/start-repl! sci-ctx) 0]))
                1)
            t1 (System/currentTimeMillis)]
        (flush)
        (when time? (binding [*out* *err*]
                      (println "bb took" (str (- t1 t0) "ms."))))
        exit-code))))

(defn -main
  [& args]
  (if-let [dev-opts (System/getenv "CLJ_JDBC_DEV")]
    (let [{:keys [:n]} (if (= "true" dev-opts) {:n 1}
                           (edn/read-string dev-opts))
          last-iteration (dec n)]
      (dotimes [i n]
        (if (< i last-iteration)
          (with-out-str (apply main args))
          (do (apply main args)
              (binding [*out* *err*]
                (println "ran" n "times"))))))
    (let [exit-code (apply main args)]
      (System/exit exit-code))))

;;;; Scratch

(comment
  )

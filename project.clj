(defproject borkdude/clj-jdbc
  #=(clojure.string/trim
     #=(slurp "resources/CLJ_JDBC_VERSION"))
  :description "clj-jdbc"
  :url "https://github.com/borkdude/clj-jdbc"
  :scm {:name "git"
        :url "https://github.com/borkdude/clj-jdbc"}
  :license {:name "Eclipse Public License 1.0"
            :url "http://opensource.org/licenses/eclipse-1.0.php"}
  :source-paths ["src" "sci/src" "clj-jdbc.curl/src"]
  ;; for debugging Reflector.java code:
  ;; :java-source-paths ["sci/reflector/src-java"]
  :resource-paths ["resources" "sci/resources"]
  :dependencies [[org.clojure/clojure "1.10.2-alpha1"]
                 [org.clojure/tools.reader "1.3.2"]
                 [borkdude/edamame "0.0.11-alpha.9"]
                 [borkdude/graal.locking "0.0.2"]
                 [borkdude/sci.impl.reflector "0.0.1"]
                 [fipp "0.6.22"]
                 [cheshire "5.10.0"]
                 [seancorfield/next.jdbc "1.0.424"]
                 [org.postgresql/postgresql "42.2.12"]
                 [org.hsqldb/hsqldb "2.4.0"]
                 [nrepl/bencode "1.1.0"]]
  :profiles {:test {:dependencies [[clj-commons/conch "0.9.2"]
                                   [com.clojure-goes-fast/clj-async-profiler "0.4.1"]]}
             :uberjar {:global-vars {*assert* false}
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.spec.skip-macros=true"]
                       :main clj-jdbc.main
                       :aot :all}
             :reflection {:main clj-jdbc.impl.classes/generate-reflection-file}}
  :aliases {"bb" ["run" "-m" "clj-jdbc.main"]}
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass
                                    :sign-releases false}]])

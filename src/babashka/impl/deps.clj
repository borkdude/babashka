(ns babashka.impl.deps
  (:require [babashka.impl.classpath :as cp]
            [borkdude.deps :as deps]
            [clojure.string :as str]
            [sci.core :as sci]
            [babashka.process :as p]))

(def dns (sci/create-ns 'dns nil))

;;;; merge deps.edn files

(defn- merge-or-replace
  "If maps, merge, otherwise replace"
  [& vals]
  (when (some identity vals)
    (reduce (fn [ret val]
              (if (and (map? ret) (map? val))
                (merge ret val)
                (or val ret)))
            nil vals)))

(defn merge-deps
  "Merge multiple deps edn maps from left to right into a single deps edn map."
  [deps-edn-maps]
  (apply merge-with merge-or-replace (remove nil? deps-edn-maps)))

(defn- merge-defaults [deps defaults]
  (let [overriden (select-keys deps (keys defaults))
        overriden-deps (keys overriden)
        defaults (select-keys defaults overriden-deps)]
    (merge deps defaults)))

(defn merge-default-deps [deps-map defaults]
  (let [paths (into [[:deps]]
                    (map (fn [alias]
                           [:aliases alias])
                         (keys (:aliases deps-map))))]
    (reduce
     (fn [acc path]
       (update-in acc path merge-defaults defaults))
     deps-map
     paths)))

#_(merge-default-deps '{:deps {medley/medley nil}
                      :aliases {:foo {medley/medley nil}}}
                    '{medley/medley {:mvn/version "1.3.0"}})

;;;; end merge edn files

;; We are optimizing for the 1-file script with deps scenario where people can
;; call this function to include e.g. {:deps {medley/medley
;; {:mvn/version "1.3.3"}}}. Optionally they can include aliases, to modify the
;; classpath.
(defn add-deps
  "Takes deps edn map and optionally a map with :aliases (seq of
  keywords) which will used to calculate classpath. The classpath is
  then used to resolve dependencies in babashka."
  ([deps-map] (add-deps deps-map nil))
  ([deps-map {:keys [:aliases]}]
   (let [args ["-Spath" "-Sdeps" (str deps-map)]
         args (cond-> args
                aliases (conj (str "-A:" (str/join ":" aliases))))
         cp (with-out-str (apply deps/-main args))]
     (cp/add-classpath cp))))

(defn- proc->Process [^java.lang.Process proc cmd prev]
  (let [stdin  (.getOutputStream proc)
        stdout (.getInputStream proc)
        stderr (.getErrorStream proc)]
    (p/->Process proc
                 nil
                 stdin
                 stdout
                 stderr
                 prev
                 cmd)))

(defn clojure
  "Starts a java process like you would normally do with the clojure
  CLI. Accepts the same arguments as the clojure CLI. If you want to
  have the equivalent of clj on linux and macOS, run bb with rlwrap."
  ;; TBD: currently this waits for the Clojure process to end and returns its
  ;; exit code.  I.e. this is the final step of our bb script. That's probably
  ;; usually what you want to do. We could have a variant called clojure* which
  ;; gives you more control, but for now this seems fine?
  ([] (clojure []))
  ([args] (clojure args nil))
  ([args opts]
   (let [opts (merge {:in :inherit
                      :out :inherit
                      :err :inherit
                      :shutdown p/destroy-tree}
                     opts)]
     (binding [*in* @sci/in
               *out* @sci/out
               *err* @sci/err
               deps/*process-fn* (fn
                                   ([cmd] (p/process cmd opts))
                                   ([cmd _] (p/process cmd opts)))
               deps/*exit-fn* (fn
                                ([_])
                                ([_exit-code msg]
                                 (throw (Exception. msg))))]
       (apply deps/-main args)))))

;; (-> (clojure ["-Sdeps" edn "-M:foo"] {:out :inherit}) p/check)

;; TODO:
;; (uberjar {:out "final.jar" :main 'foo.bar})
;; (uberscript {:out "final.clj" :main 'foo.bar})

(def deps-namespace
  {'add-deps (sci/copy-var add-deps dns)
   'clojure (sci/copy-var clojure dns)
   'merge-deps (sci/copy-var merge-deps dns)
   ;; undocumented
   'merge-defaults (sci/copy-var merge-default-deps dns)})

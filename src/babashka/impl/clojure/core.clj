(ns babashka.impl.clojure.core
  {:no-doc true}
  (:refer-clojure :exclude [future]))

(def core-extras
  {'file-seq file-seq
   'agent agent
   'send send
   'send-off send-off
   'promise promise
   'deliver deliver
   'slurp slurp
   'spit spit})

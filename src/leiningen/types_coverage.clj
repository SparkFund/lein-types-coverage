(ns leiningen.types-coverage
  "Run the project's tests."
  (:refer-clojure :exclude [test])
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [bultitude.core :as b]
            [leiningen.core.main :refer [warn]]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [leiningen.test :as lt])
  (:import (java.io File PushbackReader)))

(defn types-coverage
  "Runs tests like lein test, but checks for coverage on any type annotations
  you have. Any annotation should be exercized by at least one call."
  [project & tests]
  (binding [main/*exit-process?* (if (= :leiningen (:eval-in project))
                                   false
                                   main/*exit-process?*)
            lt/*exit-after-tests* false
            lt/*monkeypatch?* (:monkeypatch-clojure-test project true)]
    (let [project (project/merge-profiles project [:leiningen/test :test])
          [nses selectors] (lt/read-args tests project)
          form (lt/form-for-testing-namespaces nses nil (vec selectors))
          wrap-form `(types-to-schema.core/wrap-namespaces! (quote ~nses))
          all-nsses (->> (:source-paths project)
                         (map io/file)
                         (b/namespaces-on-classpath :classpath)
                         (map str)
                         (set))
          ignore-nsses (->> project :types-coverage :ignore-namespaces (map str) (set))
          ignore-vars  (->> project :types-coverage :ignore-vars (set))]
      (try (let [retval ;; as far as I can tell eval-in-project doesn't return any value? (just copying what lein test does.)
                 ,(eval/eval-in-project
                   project
                   `(let [num-errs# ~form
                          unwrapped# (->> (clojure.set/difference
                                           ;; Only consider annotations defined in our project - not libs etc.
                                           (->> (keys @clojure.core.typed.current-impl/var-env)
                                                (filter #(contains? ~all-nsses (namespace %)))
                                                (set))
                                           @types-to-schema.core/wrappers-created)
                                          (remove #(contains? ~ignore-nsses (namespace %)))
                                          (remove #(contains? (quote ~ignore-vars) %))
                                          (sort))
                          uncalled# (->> (clojure.set/difference
                                          @types-to-schema.core/wrappers-created
                                          @types-to-schema.core/wrappers-called)
                                         (remove #(contains? ~ignore-nsses (namespace %)))
                                         (remove #(contains? (quote ~ignore-vars) %))
                                         (sort))]
                      (when (not-empty unwrapped#)
                        (println "Vars annotated but never wrapped in any test namespace:")
                        (clojure.pprint/pprint unwrapped#))
                      (when (not-empty uncalled#)
                        (println "Vars wrapped but never called during any test:")
                        (clojure.pprint/pprint uncalled#))
                      (System/exit (+ num-errs# (count unwrapped#) (count uncalled#))))
                   '(require 'clojure.test
                             'types-to-schema.core
                             'clojure.set
                             'clojure.pprint
                             'clojure.core.typed.current-impl))]
             (when (and (number? retval) (pos? retval))
               (throw (ex-info "Testing Failed" {:exit-code 1}))))
           (catch clojure.lang.ExceptionInfo e
             (main/abort "Coverage-aware testing failed."))))))

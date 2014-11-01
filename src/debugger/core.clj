(ns debugger.core
  (:require clojure.reflect))

(declare ^:dynamic *locals*)

(defmacro dbg
  [x]
  `(let [x# ~x]
     (do
       (println '~x "->" x#)
       x#)))

(defn object->file [^String file obj]
  (with-open [outp (java.io.ObjectOutputStream.
                     (java.io.FileOutputStream. file))]
    (.writeObject outp obj)))

(defn public-inspect [x]
  (clojure.pprint/print-table
    (filter #(contains? (:flags %) :public)
            (map
              #(apply dissoc % [:exception-types])
              (:members (clojure.reflect/reflect x))))))

(defn prompt-fn []
  ;; (printf "%s=> " (ns-name *ns*))
  )

(defmacro local-bindings []
  (let [symbols (keys &env)]
    (zipmap (map (fn [sym] `(quote ~sym)) symbols) symbols)))

(defn eval-with-locals [locals form]
  (binding [*locals* locals]
    (eval
      `(let ~(vec (mapcat #(list % `(*locals* '~%)) (keys locals)))
         ~form))))

(defn eval-fn [env-fn cont-fn form]
  (do
    (println "> Start eval-fn")
    (case (clojure.string/trim (str form))
      "(c)" (do (println "> Eval-fn continues")
                (cont-fn))
      "(e)" (do (println "> Eval-fn env-keys")
                (env-fn))
      (do
        (println "> Eval-fn got" (pr-str form))
        (binding [*locals* (env-fn)]
          (jeval
            `(let ~(vec (mapcat #(list % `(*locals* '~%)) (keys *locals*)))
               ~form)))))))

(defn read-fn [request-prompt request-exit]
  (or ({:line-start request-prompt :stream-end request-exit}
       (clojure.main/skip-whitespace *in*))
      (let [input (read)]
        (clojure.main/skip-if-eol *in*)
        input)))

(defn caught-fn [e]
  (println "> Start caught-fn")
  (let [ex (clojure.main/repl-exception e)
        tr (.getStackTrace ex)
        el (when-not (zero? (count tr)) (aget tr 0))]
    (binding [*out* *err*]
      (println (str (-> ex class .getSimpleName)
                    " " (.getMessage ex) " "
                    (when-not (instance? clojure.lang.Compiler$CompilerException ex)
                      (str " " (if el (clojure.main/stack-element-str el) "[trace missing]"))))))))



(defmacro break [& body]
  (let [
        env (into {} (map (fn [[sym bind]] [`(quote ~sym) (.sym bind)]) &env))
        ]
    `(let [
           cont-fn# #(identity ~@body)
           env-fn# #(identity ~env)
           eval-with-cont-fn# (partial eval-fn env-fn# cont-fn#)
           ]
       (clojure.main/repl
         :prompt prompt-fn
         :eval eval-with-cont-fn#
         :read read-fn
         :caught caught-fn))))

(defn foo [& args]
  (let [
        w '(9 8)
        x "world"
        y [1 2]
        z (Object.)
        ]
    (dbg (break
      (do (dbg (+ 1 42))
          5)))
    (println "Exit foo")))


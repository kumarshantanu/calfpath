(ns calfpath.internal)


(defn parse-route
  "Given a URI pattern string, e.g. '/user/:id/profile/:descriptor/' parse it and return a vector of alternating string
  and keyword tokens, e.g. ['/user/' :id '/profile/' :descriptor '/']. The marker char is typically ':'."
  [marker-char ^String route]
  (let [n (count route)
        separator \/]
    (loop [i (int 0) ; current index in the URI string
           j (int 0) ; start index of the current token (string or keyword)
           s? true   ; string in progress? (false implies keyword in progress)
           r []]
      (if (>= i n)
        (conj r (let [t (.substring route j i)]
                  (if s?
                    t
                    (keyword t))))
        (let [ch (.charAt route i)
              [jn s? r] (if s?
                          (if (= ^char marker-char ch)
                            [(inc i) false (conj r (.substring route j i))]
                            [j true r])
                          (if (= separator ch)
                            [i true  (conj r (keyword (.substring route j i)))]
                            [j false r]))]
          (recur (inc i) (int jn) s? r))))))


(defn match-uri
  "Given a URI string and an array of pattern tokens (string or keyword only) return a map of keywords to string param
  values on a successful match, nil otherwise."
  [^String uri pattern-tokens]
  (let [n (.length uri)
        separator (int \/)]
    (loop [i (int 0) ; index into the URI
           j (int 0) ; index into pattern-tokens
           p {}      ; URI params
           ]
      (if (>= i n)
        p
        (let [t (get pattern-tokens j)
              [r? in jn pn] (if (string? t)
                              ;; match URI string from current index position
                              (when (.startsWith uri ^String t i)
                                [true (+ i (.length ^String t)) (inc j) p])
                              ;; assume keyword, extract the URI param
                              (let [stop (.indexOf uri separator i)
                                    stop (if (>= stop 0)
                                           stop
                                           n)]
                                [true stop (inc j) (assoc p t (.substring uri i stop))]))]
          (when r?
            (recur (int in) (int jn) pn)))))))


(def valid-method-keys #{:get :head :options :put :post :delete})
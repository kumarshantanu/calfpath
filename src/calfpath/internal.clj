(ns calfpath.internal)


(defn parse-uri-template
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
                            [(unchecked-inc i) false (conj r (.substring route j i))]
                            [j true r])
                          (if (= separator ch)
                            [i true  (conj r (keyword (.substring route j i)))]
                            [j false r]))]
          (recur (unchecked-inc i) (int jn) s? r))))))


(def valid-method-keys #{:get :head :options :put :post :delete})
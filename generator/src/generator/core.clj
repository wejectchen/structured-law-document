(ns generator.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]
            [generator.line :as l]
            [generator.lisp :as s]
            [generator.test :as tt]
            [generator.tokenizer :as tk]
            [generator.zh-digits :refer [numchar-zh-set]]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all])
  (:gen-class))

(defn default-fn [l] [:p l])

(defn use-chinese-paren
  {:test
   #(let [f use-chinese-paren]
      (tt/comprehend-tests
       [(t/is (= "（）"     (f "()")))
        (t/is (= "（六）"   (f "(六)")))
        (t/is (= "六）"     (f "六)")))
        (t/is (= "六）（七" (f "六)（七")))]))}
  [s]
  (str/replace s #"[\(\)]" #(case %1
                              "(" "（"
                              ")" "）")))

(defn space-clapsed
  {:test
   #(let [f space-clapsed]
      (tt/comprehend-tests
       (t/is (= "az" (f "az")))
       (t/is (= "a z" (f "a z")))
       (t/is (= "a z" (f "a  z")))
       (t/is (= "a z" (f "a　z"))) ;U+3000 - ideographic space
       ))}
  [s]
  (str/join " " (str/split s #"[\s\u3000]+")))

(defn space-filled
  {:test
   #(let [f space-filled]
      (tt/comprehend-tests
       (t/is (= "az" (f "az")))
       (t/is (= "a-z" (f "a z")))
       (t/is (= "a-z-a" (f "a z a")))))}
  [s]
  (str/replace s #"\s" "-"))

(defn within-款项
  {:test
   #(let [f within-款项
          a  (str "（三）连续订立二次固定期限劳动合同，且劳动者没有"
                  "本法第三十九条和第四十条第一项、第二项规定的情形，"
                  "续订劳动合同的。")
          sa (seq a)
          b  (str "劳动者有下列情形之一的，用人单位不得依照"
                  "本法第四十条、第四十一条的规定解除劳动合同：")
          sb (seq b)]
      (tt/comprehend-tests
       (t/is (= a (str/join (map :text (f {} sa)))))
       (t/is (= b (str/join (map :text (f {} sb)))))))}
  [context cs]
  (let [genid (partial tk/generate-id context)
        flags [#{[\本] [\前]}
               #{[\规 \定] [\法] [\条] [\款]}
               #{[\第]}]]
    (loop [cs cs ts []]
      (if (empty? cs)
        ts
        (if (s/seq-match flags cs)
          (let [[items rests] (tk/read-items cs)]
            (recur rests (into ts (-> items
                                      tk/parse
                                      (tk/update-leaves :id genid)
                                      flatten
                                      tk/second-pass))))
          (recur (rest cs) (conj ts {:token :to-be-recognized
                                     :text (str (first cs))})))))))

(defn wrap-item-string-in-html [ts]
  (let [a-x-z (fn [t])]
    [:span (let [ts' (flatten
                      (s/map-on-binary-partitions
                       :id ts
                       identity #(str/join (map :text %))))]
             (for [t ts'
                   :let [id (:id t)]]
               (if-not id
                 t
                 [:a {:href (str \# id)} (:text t)])))]))

(defn- wrap-条-in-html [head body]
  (assert (= (:token head) :条))
  [:div {:class "entry" :id (str \条 (:nth head))}
   [:div {:class "title"}
    [:b (:text head)]]
   (seq
    (loop [ps []
           [t & ts] body
           i-款 0]
      (if (nil? t)
        ps
        (condp = (:token t)
          :款 (recur (conj ps [:p {:class "款"
                                   :id (str "条" (:nth head)
                                            "款" (inc i-款))}
                               (s/map-on-binary-partitions
                                #(= (:token %) :to-be-recognized)
                                (within-款项 {:条 (:nth head)
                                              :款 (inc i-款)} (seq (:text t)))
                                #(str/join (map :text %))
                                wrap-item-string-in-html)])
                     ts
                     (inc i-款))
          :项 (recur (conj ps [:p {:class "项"
                                   :id (str "条" (:nth head)
                                            "款" i-款
                                            "项" (:nth t))}
                               (s/map-on-binary-partitions
                                #(= (:token %) :to-be-recognized)
                                (within-款项 {:条 (:nth head)
                                              :款 i-款} (seq (:text t)))
                                #(str/join (map :text %))
                                wrap-item-string-in-html)])
                     ts
                     i-款)))))])

(defn- wrap-outline-in-html [outline]
  (assert (= (:token outline) :table-of-contents))
  (let [[head & item-list] (:list outline)]
    [:nav {:id "outline"}
     [:h2 head]
     [:ul {:class "entry"}
      (for [item item-list
            :let [[i unit] (l/nth-item item)]]
        [:li [:a {:href (str "#" (name unit) i)}
              item]])]]))

(defn- wrap-in-html [tokenized-lines]
  (html
   (html5
    [:head {:lang "zh-CN"}
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     [:title (:text (first tokenized-lines))]
     [:link {:rel "stylesheet" :href "index.css"}]
     [:script {:src "main.js"}]]
    [:body
     [:div {:id "entries-container"}
      (seq
       (loop [tls tokenized-lines
              elmts []]
         (if (empty? tls)
           elmts
           (let [{t :token :as tl} (first tls)]
             (case t
               :table-of-contents
               (recur (rest tls)
                      (conj elmts (wrap-outline-in-html tl)))

               :章
               (let [txt (:text tl)]
                 (recur (rest tls)
                        (conj elmts [:h2 {:id (str "章" (:nth tl))
                                          :class "章"}
                                     txt])))

               :节
               (let [txt (:text tl)]
                 (recur (rest tls)
                        (conj elmts [:h3 {:id (str "节" (:nth tl))
                                          :class "节"}
                                     txt])))

               :条
               (let [[lines-within lines-after]
                     (split-with #(#{:款 :项} (:token %)) (rest tls))]
                 (recur lines-after
                        (conj elmts (wrap-条-in-html tl lines-within))))

               :to-be-recognized
               (recur (rest tls)
                      (conj elmts (default-fn (:text tl)))))))))]])))

(defn- tokenized-lines [ls]
  (let [[before-ts after-ls] (l/recognize-table-of-contents ls)]
    (if (empty? before-ts) ;;table of contents is not found
      (l/draw-skeleton ls)
      (into before-ts (l/draw-skeleton after-ls)))))

(defn- mainfn [inname->outpath]
  (dorun
   (for [[in out] inname->outpath]
     (with-open [r (io/reader (io/file (io/resource in)))]
       (->> (line-seq r)
            (remove str/blank?)
            (map (comp use-chinese-paren space-clapsed str/trim))
            tokenized-lines
            wrap-in-html
            (spit out))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (mainfn {"劳动合同法.txt"
           "../劳动合同法.html"

           "网络预约出租汽车经营服务管理暂行办法.txt"
           "../网络预约出租汽车经营服务管理暂行办法.html"

           "高等教育法.txt"
           "../高等教育法.html"

           "种子法.txt"
           "../种子法.html"}))

(-main)

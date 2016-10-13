(defproject generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.6.3"]
                 [clj-http "2.2.0"]
                 [org.clojure/clojure "1.8.0"]
                 [hiccup "1.0.5"]]
  :main ^:skip-aot generator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

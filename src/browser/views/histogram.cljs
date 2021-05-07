(ns browser.views.histogram
  (:require [browser.graphs :as graphs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [date-fns :as d]
            [clojure.string :as str]
            [browser.utils :as utils]))
(def acts-to-show-on-render (r/atom 5))

(defn acts-to-show-on-render-input []
  [:label "Show top"
   [:input {:type "number"
            :step 1
            :value @acts-to-show-on-render
            :on-change (fn [ev]
                         (js/console.log (->  ev .-target .-value))
                         (reset! acts-to-show-on-render  (->  ev .-target .-value int)))}]
   "activities"])

(def range* (r/atom {:start nil :end nil}))
(do
  (defn range-selection [label months default-val on-change]
    [:label
     [:span {:class "mr-2"} label]
     [:select
      {:on-change on-change}
      (map
       (fn [[y m]]
         [:option {:value [y m] :key [y m] :default-value default-val}
          (utils/fmt-ym-date y m)])
       months)]])
  (comment (range-selection "start"
                            @(rf/subscribe [:all-months-range])
                            #(= 0 %) #(swap! range* assoc :start (-> % .-target .-value)))))

(defn get-value-as-vector [ev]
  (-> ev .-target .-value
      (str/split ",")
      (->> (mapv int))))
(take-while #(not= 5 %) [2 4 5 6])
(defn main []
  (let [months @(rf/subscribe [:all-months-range])
        start-of-range (or (@range* :start) (first months))
        end-of-range (or (@range* :end) (last months))
        histogram-data @(rf/subscribe [:activities-histogram
                                       start-of-range
                                       end-of-range
                                       {:acts-to-show-on-render @acts-to-show-on-render}])]

    [:div
     [:div {:class "histogram-controls"}
      (range-selection "Range Start:"
                       (->> months (take-while #(not= % end-of-range)))
                       (first months)
                       #(swap! range* assoc :start (get-value-as-vector %)))
      (range-selection "Range End:"
                       (->> months (drop-while #(not= % start-of-range)) (drop 1))
                       (last months)
                       #(swap! range* assoc :end (get-value-as-vector %)))
      (acts-to-show-on-render-input)]
     (graphs/line "" histogram-data)]))

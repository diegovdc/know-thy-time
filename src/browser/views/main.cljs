(ns browser.views.main
  (:require [browser.budget :as budget]
            [browser.month :as month]))

(defn main
  []
  [:div
   #_[:h1 "Hello world, it is now"]
   #_[clock]
   #_[color-input]
   (budget/main)
   (month/main)])

(ns browser.db.init
  (:require [cljs.reader :as reader]))

(defn get-activities [] (reader/read-string (js/localStorage.getItem "activities")))

(defn get-categories [] (reader/read-string (js/localStorage.getItem "categories")))

(defn get-fixed-time [] (reader/read-string (js/localStorage.getItem "fixed-time")))

(defn get-day-qualities [] (reader/read-string (js/localStorage.getItem "day-qualities")))

(def initial-alert {:variant nil :msg nil})

(ns browser.utils)

(defn get-category-value [[year month] category]
    (or (get category [year month])
        (get category :default)))

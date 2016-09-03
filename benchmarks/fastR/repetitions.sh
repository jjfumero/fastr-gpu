find -iname *.R -print | xargs sed -i 's/REPETITIONS <- 0/REPETITIONS <- 1/g'

#!/bin/bash
find -iname *.R -print | xargs sed -i 's/REPETITIONS <- 1/REPETITIONS <-11/g'

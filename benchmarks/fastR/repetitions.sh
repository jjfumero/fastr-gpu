#!/bin/bash
find -iname *.R -print | xargs sed -i 's/REPETITIONS <- 11/REPETITIONS <- 1/g'

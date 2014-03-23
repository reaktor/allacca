#!/bin/bash

# This assumes they are all on one line
ag -l Log. src/ | while read F; do sed --in-place '/Log\..(/d' $F ; done

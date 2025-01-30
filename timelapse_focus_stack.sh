#!/bin/sh

# Check if the user provided a path as an argument
if [ -z "$1" ]; then
    echo "Usage: $0 /path/to/files/"
    exit 1
fi

# Store the path provided as an argument
file_path="$1"

# Loop through all files in the given path, sorted by the first sequence of zeros
for file in $(ls "${file_path}test_run"*_f*.jpg | sort -t_ -k2,2n); do
    # Extract the run number (e.g., 00000, 00001) by removing the prefix and suffix
    run_number=$(basename "$file" | sed 's/test_run\([0-9]\{5\}\)_f.*\.jpg/\1/')

    # Check if we've already processed this run_number
    if [ "$run_number" != "$prev_run_number" ]; then
        # If this is a new run_number, print the command that would be executed
        #echo "./fs --output=file${run_number}.jpg --global-align --align-keep-size --nocrop ${file_path}test_run${run_number}_f*.jpg"
        ./fs --align-keep-size --global-align --output=file${run_number}.jpg  ${file_path}test_run${run_number}_f*.jpg
        # Update the previous run_number to avoid reprocessing
        prev_run_number="$run_number"
    fi
done

ffmpeg -framerate 24 -i file%05d.jpg -c:v libx264 -pix_fmt yuv420p -preset slow -crf 18 -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" output_video.mp4

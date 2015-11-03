Termux API
==========
This is an app exposing Android API to command line usage and scripts or programs.

License
=======
Released under the [GPLv3 license](http://www.gnu.org/licenses/gpl-3.0.en.html).

The termux-api client helper binary
===================================
The client helper binary ([termux-api.c](https://github.com/termux/termux-packages/blob/master/packages/termux-api/termux-api.c))
generates two linux anonymous namespace sockets, and passes their address as in:
	
	/system/bin/am broadcast ${SERVICE_CLASS} --es socket_input ${INPUT_SOCKET} --es socket_output ${OUTPUT_SOCKET}

where the sockets are used to transfer:

- input through stdin to the helper binary are forwarded to java code
- java code may output feedback which are forwarded to the stdout of the helper binary

Client scripts
==============
Client scripts which processes command line arguments before calling the termux-api helper binary are available in [the termux-api package](https://github.com/termux/termux-packages/tree/master/packages/termux-api).

Ideas
=====
- Method for playing audio files using the system MediaPlayer, so `termux-play myfile.ogg` would play the audio file.
- Wifi network search and connect.
- Proximity sensor.
- Accelerometer.
- LED Flash.

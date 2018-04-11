Termux API
==========
[![Join the chat at https://gitter.im/termux/termux](https://badges.gitter.im/termux/termux.svg)](https://gitter.im/termux/termux)

This is an app exposing Android API to command line usage and scripts or programs.

- [Termux:API on Google Play](https://play.google.com/store/apps/details?id=com.termux.api)

When developing or packaging, note that this app needs to be signed with the same key as the main Termux app for permissions to work (only the main Termux app are allowed to call the API methods in this app).

License
=======
Released under the [GPLv3 license](http://www.gnu.org/licenses/gpl-3.0.en.html).

How API calls are made through the termux-api helper binary
===========================================================
The [termux-api](https://github.com/termux/termux-api-package/blob/master/termux-api.c) client binary in the `termux-api` package generates two linux anonymous namespace sockets, and passes their address to the [TermuxApiReceiver broadcast receiver](https://github.com/termux/termux-api/blob/master/app/src/main/java/com/termux/api/TermuxApiReceiver.java) as in:
	
	/system/bin/am broadcast ${BROADCAST_RECEIVER} --es socket_input ${INPUT_SOCKET} --es socket_output ${OUTPUT_SOCKET}

The two sockets are used to forward stdin from `termux-api` to the relevant API class and output from the API class to the stdout of `termux-api`.

Client scripts
==============
Client scripts which processes command line arguments before calling the `termux-api` helper binary are available in [the termux-api package](https://github.com/termux/termux-api-package).

Ideas
=====
- Wifi network search and connect.
- Add extra permissions to the app to (un)install apps, stop processes etc.

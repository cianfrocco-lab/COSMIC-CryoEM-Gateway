# COSMIC-CryoEM-Gateway
The gateway_config/ subdirectory contains code custom to the COSMIC2 CryoEM
Science Gateway.  The rest of the gateway's code is in the CIPRES svn at
https://svn.sdsc.edu/repo/scigap/trunk.

Notes from Choonhan's globus integration section:
The gateway’s storage destination endpoint combines a SDSC-wide Globus endpoint and a custom security credential for our XSEDE community account. The Globus “delegation_proxy” activation type is applied to the community account which has a copy of the user’s credential and is supported by all GridFTP endpoints. The “activation_requirements” transfer API returns the activation requirement document that contains a list of activation types supported by our endpoint. The delegation activation type in this document provides the public key so that the proxy generator can create a delegated X.509 proxy credential signed by the community account’s credential. At the time of the transfer task submission, two parameters are needed: “encrypt_data=true” which specifies encrypted transfer and “sync_level=2” which specifies to copy the file only if the timestamp of the destination is older than the timestamp of the source. The result of the transfer task is stored in the gateway database.

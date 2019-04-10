# Compiling Client
```
bash compile
```

# Running Client

### Foreground
```
bash client <server-ip> <mins>
```

### Background
```
bash client <server-ip> <mins> &
```

Options:
* server-ip: IP address of the server.
* mins: Time for which the client should run in minutes.

# Output Files

* report.log: Contains statistics for the run.
* histo.txt: Contains histogram for the drift rates calculated for the run.
* log.txt: Contains the details of each interaction with server.


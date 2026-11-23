// C functions from the Go Tailscale bridge (tailscale-bridge/cmd/iosbridge).
// Returned strings are owned by the caller: release them with TSFree.
#ifndef TSBRIDGE_H
#define TSBRIDGE_H

char *TSStart(const char *dir, const char *hostname);   // NULL on success, else an error
void TSStop(void);
char *TSStatus(void);                                    // JSON
char *TSLogin(void);
char *TSLogout(void);
int TSProbe(const char *host, int ms);                   // 1 when host:80 answers
int TSProxyPort(void);                                   // local HTTP proxy port, -1 on error
char *TSLogs(void);
void TSFree(char *p);

#endif

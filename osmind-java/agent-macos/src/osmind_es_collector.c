#include <EndpointSecurity/EndpointSecurity.h>
#include <bsm/libbsm.h>
#include <errno.h>
#include <libproc.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

static volatile sig_atomic_t keep_running = 1;

static void handle_signal(int signal_number) {
    (void) signal_number;
    keep_running = 0;
}

static char *copy_token(es_string_token_t token) {
    char *value = calloc(token.length + 1, sizeof(char));
    if (value == NULL) {
        return NULL;
    }
    if (token.data != NULL && token.length > 0) {
        memcpy(value, token.data, token.length);
    }
    value[token.length] = '\0';
    return value;
}

static const char *basename_ptr(const char *path) {
    if (path == NULL || path[0] == '\0') {
        return "unknown";
    }
    const char *slash = strrchr(path, '/');
    return slash == NULL ? path : slash + 1;
}

static long process_pid(const es_process_t *process) {
    if (process == NULL) {
        return 0;
    }
    return (long) audit_token_to_pid(process->audit_token);
}

static long process_ppid(long pid) {
    struct proc_bsdinfo info;
    int size = proc_pidinfo((int) pid, PROC_PIDTBSDINFO, 0, &info, sizeof(info));
    if (size == sizeof(info)) {
        return (long) info.pbi_ppid;
    }
    return 0;
}

static char *process_executable_path(const es_process_t *process) {
    if (process == NULL || process->executable == NULL) {
        return strdup("");
    }
    return copy_token(process->executable->path);
}

static void iso8601_now(char *buffer, size_t buffer_size) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tm;
    gmtime_r(&ts.tv_sec, &tm);
    strftime(buffer, buffer_size, "%Y-%m-%dT%H:%M:%SZ", &tm);
}

static void json_escape(FILE *out, const char *value) {
    if (value == NULL) {
        return;
    }
    for (const unsigned char *cursor = (const unsigned char *) value; *cursor != '\0'; cursor++) {
        switch (*cursor) {
            case '\\':
                fputs("\\\\", out);
                break;
            case '"':
                fputs("\\\"", out);
                break;
            case '\n':
                fputs("\\n", out);
                break;
            case '\r':
                fputs("\\r", out);
                break;
            case '\t':
                fputs("\\t", out);
                break;
            default:
                if (*cursor < 0x20) {
                    fprintf(out, "\\u%04x", *cursor);
                } else {
                    fputc(*cursor, out);
                }
        }
    }
}

static void write_json_field(FILE *out, const char *key, const char *value, bool comma) {
    fprintf(out, "\"%s\":\"", key);
    json_escape(out, value);
    fputc('"', out);
    if (comma) {
        fputc(',', out);
    }
}

static void emit_event(FILE *out,
                       const char *type,
                       const es_process_t *process,
                       const char *target,
                       long bytes,
                       const char *attrs) {
    char timestamp[32];
    iso8601_now(timestamp, sizeof(timestamp));

    long pid = process_pid(process);
    long ppid = process_ppid(pid);
    char *exe = process_executable_path(process);
    const char *name = basename_ptr(exe);

    fputc('{', out);
    write_json_field(out, "ts", timestamp, true);
    write_json_field(out, "agent", "macos-endpoint-security", true);
    write_json_field(out, "type", type, true);
    fprintf(out, "\"pid\":\"%ld\",", pid);
    fprintf(out, "\"ppid\":\"%ld\",", ppid);
    write_json_field(out, "name", name, true);
    write_json_field(out, "exe", exe, true);
    write_json_field(out, "cwd", "", true);
    write_json_field(out, "cmd", name, true);
    write_json_field(out, "target", target == NULL ? "" : target, true);
    fprintf(out, "\"bytes\":\"%ld\",", bytes);
    write_json_field(out, "attrs", attrs == NULL ? "" : attrs, false);
    fputs("}\n", out);
    fflush(out);

    free(exe);
}

static char *file_path(const es_file_t *file) {
    if (file == NULL) {
        return strdup("");
    }
    return copy_token(file->path);
}

static void handle_message(FILE *out, const es_message_t *message) {
    switch (message->event_type) {
        case ES_EVENT_TYPE_NOTIFY_EXEC: {
            const es_process_t *target_process = message->event.exec.target;
            char *target = process_executable_path(target_process);
            emit_event(out, "EXEC", target_process, target, 0, "source=EndpointSecurity.exec");
            free(target);
            break;
        }
        case ES_EVENT_TYPE_NOTIFY_OPEN: {
            char *target = file_path(message->event.open.file);
            emit_event(out, "OPEN", message->process, target, 0, "source=EndpointSecurity.open");
            free(target);
            break;
        }
        case ES_EVENT_TYPE_NOTIFY_WRITE: {
            char *target = file_path(message->event.write.target);
            emit_event(out, "WRITE", message->process, target, 0, "source=EndpointSecurity.write");
            free(target);
            break;
        }
        case ES_EVENT_TYPE_NOTIFY_UNLINK: {
            char *target = file_path(message->event.unlink.target);
            emit_event(out, "UNLINK", message->process, target, 0, "source=EndpointSecurity.unlink");
            free(target);
            break;
        }
        case ES_EVENT_TYPE_NOTIFY_SETMODE: {
            char *target = file_path(message->event.setmode.target);
            char attrs[64];
            snprintf(attrs, sizeof(attrs), "source=EndpointSecurity.setmode&mode=%o", message->event.setmode.mode);
            emit_event(out, "CHMOD", message->process, target, 0, attrs);
            free(target);
            break;
        }
        default:
            break;
    }
}

static FILE *open_output(void) {
    const char *explicit_store = getenv("OSMIND_STORE");
    if (explicit_store != NULL && explicit_store[0] != '\0') {
        FILE *out = fopen(explicit_store, "a");
        if (out != NULL) {
            return out;
        }
        perror("Could not open OSMIND_STORE");
        exit(2);
    }

    const char *osmind_home = getenv("OSMIND_HOME");
    if (osmind_home != NULL && osmind_home[0] != '\0') {
        if (mkdir(osmind_home, 0700) != 0 && errno != EEXIST) {
            perror("Could not create OSMIND_HOME");
            exit(2);
        }
        char path[PROC_PIDPATHINFO_MAXSIZE];
        snprintf(path, sizeof(path), "%s/events.jsonl", osmind_home);
        FILE *out = fopen(path, "a");
        if (out != NULL) {
            return out;
        }
        perror("Could not open OSMIND_HOME/events.jsonl");
        exit(2);
    }

    const char *home = getenv("HOME");
    if (home == NULL || home[0] == '\0') {
        fputs("HOME is not set and neither OSMIND_STORE nor OSMIND_HOME was provided.\n", stderr);
        exit(2);
    }

    char dir[PROC_PIDPATHINFO_MAXSIZE];
    snprintf(dir, sizeof(dir), "%s/.osmind", home);
    if (mkdir(dir, 0700) != 0 && errno != EEXIST) {
        perror("Could not create ~/.osmind");
        exit(2);
    }

    char path[PROC_PIDPATHINFO_MAXSIZE];
    snprintf(path, sizeof(path), "%s/events.jsonl", dir);
    FILE *out = fopen(path, "a");
    if (out == NULL) {
        perror("Could not open ~/.osmind/events.jsonl");
        exit(2);
    }
    return out;
}

int main(void) {
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);

    FILE *out = open_output();
    es_client_t *client = NULL;

    es_new_client_result_t result = es_new_client(&client, ^(es_client_t *handler_client, const es_message_t *message) {
        (void) handler_client;
        handle_message(out, message);
    });

    if (result != ES_NEW_CLIENT_RESULT_SUCCESS) {
        fprintf(stderr, "es_new_client failed with code %d.\n", result);
        fprintf(stderr, "Endpoint Security requires a properly signed binary with com.apple.developer.endpoint-security.client entitlement.\n");
        fclose(out);
        return 1;
    }

    es_event_type_t events[] = {
            ES_EVENT_TYPE_NOTIFY_EXEC,
            ES_EVENT_TYPE_NOTIFY_OPEN,
            ES_EVENT_TYPE_NOTIFY_WRITE,
            ES_EVENT_TYPE_NOTIFY_UNLINK,
            ES_EVENT_TYPE_NOTIFY_SETMODE
    };

    if (es_subscribe(client, events, sizeof(events) / sizeof(events[0])) != ES_RETURN_SUCCESS) {
        fputs("es_subscribe failed.\n", stderr);
        es_delete_client(client);
        fclose(out);
        return 1;
    }

    fprintf(stderr, "OSMind macOS Endpoint Security collector started. Writing events to OSMind JSONL storage.\n");
    while (keep_running) {
        pause();
    }

    es_unsubscribe_all(client);
    es_delete_client(client);
    fclose(out);
    return 0;
}

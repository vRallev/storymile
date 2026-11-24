package software.ralf.storymile.storage

import kotlin.js.JsAny
import kotlin.js.Promise

internal external interface BrowserFileStorage : JsAny {
  fun hasNamespace(namespace: String): Promise<JsAny?>

  fun copyPreferences(sourceNamespace: String, targetNamespace: String)

  fun deleteNamespace(namespace: String): Promise<JsAny?>

  fun readFile(namespace: String, area: String, name: String): Promise<JsAny?>

  fun writeFile(namespace: String, area: String, name: String, content: JsAny): Promise<JsAny?>

  fun deleteFile(namespace: String, area: String, name: String): Promise<JsAny?>

  fun deleteAllFiles(namespace: String, area: String, path: String): Promise<JsAny?>

  fun createBytes(size: Int): JsAny

  fun byteLength(content: JsAny): Int

  fun readByte(content: JsAny, index: Int): Int

  fun writeByte(content: JsAny, index: Int, value: Int)
}

@JsFun(
  """() => {
    async function locateEntry(namespace, area, path, create) {
      const segments = ['storymile', 'storage', namespace, area];
      if (path !== '') segments.push(...path.split('/'));
      const name = segments.pop();
      let folder = await navigator.storage.getDirectory();
      for (const segment of segments) {
        folder = await folder.getDirectoryHandle(segment, { create });
      }
      return { folder, name };
    }
    function preferencesPrefix(namespace) {
      return 'storymile.storage/' + namespace + '/preferences/';
    }
    function preferenceKeys(namespace) {
      return Object.keys(localStorage).filter(key => key.startsWith(preferencesPrefix(namespace)));
    }
    async function namespaceParent() {
      const root = await navigator.storage.getDirectory();
      const storymile = await root.getDirectoryHandle('storymile');
      return storymile.getDirectoryHandle('storage');
    }
    return {
      async hasNamespace(namespace) {
        if (preferenceKeys(namespace).length) return true;
        try {
          const parent = await namespaceParent();
          await parent.getDirectoryHandle(namespace);
          return true;
        } catch (error) {
          if (error.name === 'NotFoundError') return false;
          throw error;
        }
      },
      copyPreferences(sourceNamespace, targetNamespace) {
        const source = preferencesPrefix(sourceNamespace);
        const target = preferencesPrefix(targetNamespace);
        for (const key of preferenceKeys(sourceNamespace)) {
          localStorage.setItem(target + key.slice(source.length), localStorage.getItem(key));
        }
      },
      async deleteNamespace(namespace) {
        const prefix = preferencesPrefix(namespace);
        for (const key of Object.keys(localStorage)) {
          if (key.startsWith(prefix) || key.startsWith('datastore_LOCAL_' + prefix)) {
            localStorage.removeItem(key);
          }
        }
        try {
          const parent = await namespaceParent();
          await parent.removeEntry(namespace, { recursive: true });
        } catch (error) {
          if (error.name !== 'NotFoundError') throw error;
        }
      },
      async readFile(namespace, area, name) {
        try {
          const location = await locateEntry(namespace, area, name, false);
          const handle = await location.folder.getFileHandle(location.name);
          const file = await handle.getFile();
          return new Uint8Array(await file.arrayBuffer());
        } catch (error) {
          if (error.name === 'NotFoundError') return null;
          throw error;
        }
      },
      writeFile(namespace, area, name, content) {
        let cancelled = false;
        let writable = null;
        function checkCancellation() {
          if (cancelled) throw new DOMException('Storage write cancelled.', 'AbortError');
        }
        const promise = (async () => {
          try {
            const location = await locateEntry(namespace, area, name, true);
            checkCancellation();
            const handle = await location.folder.getFileHandle(location.name, { create: true });
            checkCancellation();
            writable = await handle.createWritable();
            checkCancellation();
            await writable.write(content);
            checkCancellation();
            await writable.close();
            writable = null;
            return null;
          } catch (error) {
            if (writable !== null) {
              try { await writable.abort(); } catch (_) {}
            }
            throw error;
          }
        })();
        promise.cancel = () => { cancelled = true; };
        return promise;
      },
      deleteFile(namespace, area, name) {
        let cancelled = false;
        const promise = (async () => {
          try {
            const location = await locateEntry(namespace, area, name, false);
            if (cancelled) throw new DOMException('Storage delete cancelled.', 'AbortError');
            await location.folder.getFileHandle(location.name);
            if (cancelled) throw new DOMException('Storage delete cancelled.', 'AbortError');
            await location.folder.removeEntry(location.name);
          } catch (error) {
            if (error.name !== 'NotFoundError') throw error;
          }
          return null;
        })();
        promise.cancel = () => { cancelled = true; };
        return promise;
      },
      deleteAllFiles(namespace, area, path) {
        let cancelled = false;
        const promise = (async () => {
          try {
            const location = await locateEntry(namespace, area, path, false);
            if (cancelled) throw new DOMException('Storage clear cancelled.', 'AbortError');
            await location.folder.getDirectoryHandle(location.name);
            if (cancelled) throw new DOMException('Storage clear cancelled.', 'AbortError');
            await location.folder.removeEntry(location.name, { recursive: true });
          } catch (error) {
            if (error.name !== 'NotFoundError') throw error;
          }
          return null;
        })();
        promise.cancel = () => { cancelled = true; };
        return promise;
      },
      createBytes(size) { return new Uint8Array(size); },
      byteLength(content) { return content.length; },
      readByte(content, index) { return content[index]; },
      writeByte(content, index, value) { content[index] = value; }
    };
  }""",
)
internal external fun createBrowserFileStorage(): BrowserFileStorage

@JsFun("(operation) => operation.cancel()")
internal external fun cancelBrowserFileOperation(operation: Promise<JsAny?>)

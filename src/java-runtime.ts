import path from 'node:path';
import { download, json, safePath } from './game-install';

const INDEX = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
type Fetcher = typeof fetch;
type RuntimeFile = { type: string; downloads?: { raw?: { sha1: string; size: number; url: string } } };

// Downloads Mojang's official Java runtime with SHA-1 checks, the same one the official launcher uses.
export async function installJavaRuntime(gameDirectory: string, component: string, progress: (done: number, total: number) => void, fetcher: Fetcher = fetch): Promise<string> {
  if (!/^[a-z0-9-]+$/.test(component)) throw new Error('Invalid Java runtime name.');
  const index = await json(INDEX, fetcher);
  const entry = index?.['windows-x64']?.[component]?.[0];
  if (!entry?.manifest?.url || !/^[a-f0-9]{40}$/.test(entry.manifest.sha1)) throw new Error('This Java runtime is unavailable.');
  const manifest = await json(entry.manifest.url, fetcher, entry.manifest.sha1);
  const runtime = path.join(gameDirectory, 'runtime', component);
  const files = Object.entries((manifest.files || {}) as Record<string, RuntimeFile>).filter(([, file]) => file.type === 'file');
  if (!files.some(([name]) => name === 'bin/java.exe')) throw new Error('The Java runtime has no java.exe.');
  const jobs = files.map(([name, file]) => {
    const raw = file.downloads?.raw;
    if (!raw) throw new Error('Incomplete Java runtime metadata.');
    return { file: { path: name, url: raw.url, sha1: raw.sha1, size: raw.size }, target: safePath(runtime, name) };
  });
  let next = 0, done = 0;
  let failure: unknown;
  progress(0, jobs.length);
  await Promise.all(Array.from({ length: 6 }, async () => {
    while (!failure && next < jobs.length) {
      const job = jobs[next++];
      try { await download(job.file, job.target, gameDirectory, fetcher); progress(++done, jobs.length); }
      catch (error) { failure ||= error; }
    }
  }));
  if (failure) throw failure;
  return path.join(runtime, 'bin', 'java.exe');
}

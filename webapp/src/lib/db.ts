import { readFile, writeFile } from 'node:fs/promises';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const DATA_DIR = path.join(process.cwd(), 'data');

export type GameStatus = 'wishlist' | 'backlog' | 'playing' | 'beaten' | 'paused' | 'quit';

export interface Game {
  id: string;
  title: string;
  platform: string;
  year?: number;
  coverUrl?: string;
  description?: string;
}

export interface LibraryEntry {
  username: string;
  gameId: string;
  status: GameStatus;
  updatedAt: string;
}

export interface GameList {
  id: string;
  username: string;
  name: string;
  description?: string;
  gameIds: string[];
}

export interface User {
  username: string;
  displayName: string;
}

async function readJson<T>(file: string, fallback: T): Promise<T> {
  const filePath = path.join(DATA_DIR, file);
  if (!existsSync(filePath)) return fallback;
  const raw = await readFile(filePath, 'utf-8');
  return raw.trim() ? JSON.parse(raw) : fallback;
}

async function writeJson(file: string, data: unknown) {
  if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true });
  await writeFile(path.join(DATA_DIR, file), JSON.stringify(data, null, 2));
}

// --- Users ---
export const getUsers = () => readJson<User[]>('users.json', []);
export const getUser = async (username: string) =>
  (await getUsers()).find((u) => u.username === username) ?? null;

// --- Games ---
export const getGames = () => readJson<Game[]>('games.json', []);

export async function getGame(id: string) {
  return (await getGames()).find((g) => g.id === id) ?? null;
}

export async function searchGames(query: string) {
  const games = await getGames();
  if (!query.trim()) return games;
  const q = query.toLowerCase();
  return games.filter((g) => g.title.toLowerCase().includes(q));
}

export async function addGame(input: Omit<Game, 'id'>) {
  const games = await getGames();
  const existing = games.find(
    (g) => g.title.toLowerCase() === input.title.toLowerCase()
  );
  if (existing) return existing;
  const game: Game = { id: randomUUID(), ...input };
  games.push(game);
  await writeJson('games.json', games);
  return game;
}

// --- Library (estado por usuario) ---
export const getLibrary = () => readJson<LibraryEntry[]>('library.json', []);

export async function getLibraryForUser(username: string) {
  return (await getLibrary()).filter((e) => e.username === username);
}

export async function getStatus(username: string, gameId: string) {
  const entry = (await getLibrary()).find(
    (e) => e.username === username && e.gameId === gameId
  );
  return entry?.status ?? null;
}

export async function setStatus(username: string, gameId: string, status: GameStatus) {
  const library = await getLibrary();
  const entry = library.find((e) => e.username === username && e.gameId === gameId);
  if (entry) {
    entry.status = status;
    entry.updatedAt = new Date().toISOString();
  } else {
    library.push({ username, gameId, status, updatedAt: new Date().toISOString() });
  }
  await writeJson('library.json', library);
}

// --- Lists ---
export const getLists = () => readJson<GameList[]>('lists.json', []);

export async function getListsForUser(username: string) {
  return (await getLists()).filter((l) => l.username === username);
}

export async function getList(id: string) {
  return (await getLists()).find((l) => l.id === id) ?? null;
}

export async function createList(username: string, name: string, description?: string) {
  const lists = await getLists();
  const list: GameList = { id: randomUUID(), username, name, description, gameIds: [] };
  lists.push(list);
  await writeJson('lists.json', lists);
  return list;
}

export async function deleteList(id: string) {
  const lists = (await getLists()).filter((l) => l.id !== id);
  await writeJson('lists.json', lists);
}

export async function addGameToList(listId: string, gameId: string) {
  const lists = await getLists();
  const list = lists.find((l) => l.id === listId);
  if (list && !list.gameIds.includes(gameId)) {
    list.gameIds.push(gameId);
    await writeJson('lists.json', lists);
  }
}

export async function removeGameFromList(listId: string, gameId: string) {
  const lists = await getLists();
  const list = lists.find((l) => l.id === listId);
  if (list) {
    list.gameIds = list.gameIds.filter((id) => id !== gameId);
    await writeJson('lists.json', lists);
  }
}

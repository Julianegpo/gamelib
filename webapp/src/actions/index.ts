import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import * as db from '../lib/db';

export const server = {
  addGame: defineAction({
    accept: 'form',
    input: z.object({
      title: z.string().min(1),
      platform: z.string().min(1),
      year: z.coerce.number().optional(),
      coverUrl: z.string().optional(),
      description: z.string().optional(),
    }),
    handler: async (input) => {
      const game = await db.addGame(input);
      return { id: game.id };
    },
  }),

  setStatus: defineAction({
    accept: 'form',
    input: z.object({
      username: z.string(),
      gameId: z.string(),
      status: z.enum(['wishlist', 'backlog', 'playing', 'beaten', 'paused', 'quit']),
    }),
    handler: async ({ username, gameId, status }) => {
      await db.setStatus(username, gameId, status);
      return { ok: true };
    },
  }),

  createList: defineAction({
    accept: 'form',
    input: z.object({
      username: z.string(),
      name: z.string().min(1),
      description: z.string().optional(),
    }),
    handler: async ({ username, name, description }) => {
      const list = await db.createList(username, name, description);
      return { id: list.id };
    },
  }),

  deleteList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string() }),
    handler: async ({ listId }) => {
      await db.deleteList(listId);
      return { ok: true };
    },
  }),

  addGameToList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string(), gameId: z.string() }),
    handler: async ({ listId, gameId }) => {
      await db.addGameToList(listId, gameId);
      return { ok: true };
    },
  }),

  removeGameFromList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string(), gameId: z.string() }),
    handler: async ({ listId, gameId }) => {
      await db.removeGameFromList(listId, gameId);
      return { ok: true };
    },
  }),
};

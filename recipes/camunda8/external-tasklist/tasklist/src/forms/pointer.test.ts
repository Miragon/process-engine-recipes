import { describe, expect, test } from 'vitest';
import { fromPointer, toPointer } from './pointer';

describe('JSON pointers', () => {
  test('escape ~ and / in segments and read them back', () => {
    expect(toPointer(['items', '0', 'a/b', 'c~d'])).toBe('/items/0/a~1b/c~0d');
    expect(fromPointer('/items/0/a~1b/c~0d')).toEqual(['items', '0', 'a/b', 'c~d']);
    expect(fromPointer('')).toEqual([]);
  });

  test('decode URI fragments, but take plain pointers literally', () => {
    expect(fromPointer('#/properties/first%20name')).toEqual(['properties', 'first name']);
    expect(fromPointer('#')).toEqual([]);
    expect(fromPointer('/discount%')).toEqual(['discount%']);
  });
});

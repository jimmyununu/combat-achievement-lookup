package com.calookup;

import net.runelite.api.gameval.VarPlayerID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombatTaskRepositoryTest
{
	@Test
	public void completionBitIsTaskIdModulo32InVarpIdOver32()
	{
		int[] varps = new int[CombatTaskRepository.COMPLETED_VARPS.length];
		varps[0] = 1;            // task 0
		varps[1] = 1 << 5;       // task 37
		varps[20] = 1 << 31;     // task 671

		assertTrue(CombatTaskRepository.isCompleted(varps, 0));
		assertFalse(CombatTaskRepository.isCompleted(varps, 1));
		assertTrue(CombatTaskRepository.isCompleted(varps, 37));
		assertFalse(CombatTaskRepository.isCompleted(varps, 36));
		assertTrue(CombatTaskRepository.isCompleted(varps, 671));
	}

	@Test
	public void outOfRangeIdsAreNotCompleted()
	{
		int[] varps = new int[3];
		assertFalse(CombatTaskRepository.isCompleted(varps, -1));
		assertFalse(CombatTaskRepository.isCompleted(varps, 96));
		assertFalse(CombatTaskRepository.isCompleted(new int[0], 0));
	}

	@Test
	public void completionVarpsAreRecognised()
	{
		assertTrue(CombatTaskRepository.isCompletionVarp(VarPlayerID.CA_TASK_COMPLETED_0));
		assertTrue(CombatTaskRepository.isCompletionVarp(VarPlayerID.CA_TASK_COMPLETED_20));
		assertFalse(CombatTaskRepository.isCompletionVarp(VarPlayerID.CA_TRACKING));
		assertEquals(21, CombatTaskRepository.COMPLETED_VARPS.length);
	}
}

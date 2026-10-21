package dev.sporecell.core;

/** Independent Java reconstruction of the observed cell rules. Native evidence: e51410/e58980/e7ce10. */
public final class Evolution {
    private Evolution() {}
    public static final int MAX_PROGRESS=1000, MAX_HEALTH=6;
    private static final int[] DNA={0,5,10,13,16,19,22,25,28,31,34,37,40,43,46,49,52,55,58,61,65};
    private static final int[][] NPC_DAMAGE={{0,0,0,0,0},{6,2,1,0,0},{6,6,2,0,0},{6,6,6,2,0},{6,6,6,6,2}};
    public enum Diet { HERBIVORE, CARNIVORE, OMNIVORE }
    public static int earnedDna(int progress) {
        int p=Math.clamp(progress,0,MAX_PROGRESS),row=p/50;
        if(row==20)return DNA[20];
        return (int)Math.rint(DNA[row]+(DNA[row+1]-DNA[row])*(p%50)/50.0);
    }
    public static int level(int progress) { return Math.min(10,1+Math.clamp(progress,0,1000)/100); }
    public static int category(int cellLevel,int playerLevel) { return Math.clamp(cellLevel-playerLevel+2,0,4); }
    public static int damage(boolean playerAttacks,boolean playerVictim,int attackerCategory,int victimCategory,boolean hard) {
        int a=Math.clamp(attackerCategory,0,4),v=Math.clamp(victimCategory,0,4);
        if(playerAttacks)return v<2?6:v==2?(hard?2:3):1;
        if(playerVictim)return a<2?1:a==2?2:a==3?3:6;
        return NPC_DAMAGE[a][v];
    }
    public static Diet diet(boolean plant,boolean meat) { return plant&&meat?Diet.OMNIVORE:meat?Diet.CARNIVORE:Diet.HERBIVORE; }
}

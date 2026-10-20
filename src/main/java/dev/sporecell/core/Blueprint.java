package dev.sporecell.core;

import java.util.*;

/** Coordinates are local X/Z; direction 0 points north (-Z), then clockwise. */
public record Blueprint(List<Piece> pieces) {
    public static final int RADIUS = 4;
    public record Piece(int x, int z, Part part, int direction) {}
    public record Stats(int cost, double health, double speed, double turn, double sight, double damage,
                        boolean herbivore, boolean carnivore) {}

    public Blueprint { pieces = List.copyOf(pieces); }
    public static Blueprint starter() {
        return new Blueprint(List.of(new Piece(0, 0, Part.CORE, 0),
                new Piece(0, -1, Part.HERBIVORE_MOUTH, 0), new Piece(0, 1, Part.FLAGELLUM, 2)));
    }
    public void validate() {
        if (pieces.isEmpty() || pieces.size() > 49) throw new IllegalArgumentException("Use 1..49 parts");
        Map<String, Piece> occupied = new HashMap<>();
        int cores = 0;
        for (Piece p : pieces) {
            if (p.part == null || Math.abs(p.x) > RADIUS || Math.abs(p.z) > RADIUS || p.direction < 0 || p.direction > 3)
                throw new IllegalArgumentException("Part outside grid or invalid rotation");
            if (occupied.put(key(p.x, p.z), p) != null) throw new IllegalArgumentException("Overlapping parts");
            if (p.part == Part.CORE) { cores++; if (p.x != 0 || p.z != 0) throw new IllegalArgumentException("Core must be centered"); }
        }
        if (cores != 1) throw new IllegalArgumentException("Exactly one central core is required");
        Set<String> visited = new HashSet<>();
        ArrayDeque<Piece> queue = new ArrayDeque<>(); queue.add(occupied.get(key(0,0)));
        while (!queue.isEmpty()) {
            Piece p = queue.remove(); if (!visited.add(key(p.x,p.z))) continue;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Piece next = occupied.get(key(p.x+d[0],p.z+d[1]));
                if (next != null && !visited.contains(key(next.x,next.z))) queue.add(next);
            }
        }
        if (visited.size() != pieces.size()) throw new IllegalArgumentException("All parts must connect to the core");
    }
    private static String key(int x,int z) { return x + ":" + z; }
    public Stats stats(Map<Part, PartSpec> specs) {
        validate(); int cost=0; double hp=0, thrust=0, turn=0, sight=0, damage=0;
        boolean herb=false, carn=false;
        for (Piece p : pieces) {
            PartSpec s = Objects.requireNonNull(specs.get(p.part)); cost += s.cost(); hp += s.health();
            // Forward/rear mounting supplies propulsion; side mounting gives less thrust.
            double alignment = (p.direction % 2 == 0) ? 1 : .55;
            thrust += s.thrust() * alignment;
            turn += s.turn() * (1 + Math.abs(p.x) * .15);
            sight += s.sight(); damage = Math.max(damage,s.damage());
            herb |= p.part == Part.HERBIVORE_MOUTH; carn |= p.part == Part.CARNIVORE_MOUTH;
        }
        return new Stats(cost, Math.max(1,hp), Math.min(.38,(.075+thrust)/Math.sqrt(pieces.size()*.35)),
                Math.min(18, 3+turn), Math.min(64,6+sight), damage, herb,carn);
    }
    public static int purchaseCost(Blueprint old, Blueprint next, Map<Part,PartSpec> specs) {
        // Full salvage value prevents charging again for an existing body.
        return next.stats(specs).cost - old.stats(specs).cost;
    }
}

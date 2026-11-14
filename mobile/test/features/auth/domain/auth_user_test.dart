import 'package:flutter_test/flutter_test.dart';
import 'package:barberia_app/features/auth/domain/entities/auth_user.dart';

// ─── Fixtures ────────────────────────────────────────────────────────────────

AuthUser _makeOwner({
  String userId = 'user-1',
  String primaryRole = 'OWNER',
  List<String>? capabilities,
  TenantInfo? tenant,
  ClientProfileInfo? clientProfile,
}) =>
    AuthUser(
      userId: userId,
      token: 'tok',
      refreshToken: 'ref',
      capabilities: capabilities ?? ['OWNER', 'CLIENT'],
      primaryRole: primaryRole,
      tenant: tenant ??
          const TenantInfo(id: 't1', name: "Barba's Club"),
      clientProfile: clientProfile ??
          const ClientProfileInfo(id: 'cp1', name: 'Roberto Gómez'),
    );

AuthUser _makeClient({
  String name = 'Juan Pérez',
  bool isPenalized = false,
}) =>
    AuthUser(
      userId: 'user-2',
      token: 'tok',
      refreshToken: 'ref',
      capabilities: const ['CLIENT'],
      primaryRole: 'CLIENT',
      clientProfile: ClientProfileInfo(
        id: 'cp2',
        name: name,
        isPenalized: isPenalized,
      ),
    );

void main() {
  group('AuthUser', () {

    group('capabilities', () {
      test('isOwner true cuando tiene OWNER', () {
        final u = _makeOwner(capabilities: ['OWNER', 'CLIENT']);
        expect(u.isOwner, isTrue);
        expect(u.isBarber, isFalse);
        expect(u.isClient, isTrue);
      });

      test('isBarber true cuando tiene BARBER', () {
        final u = AuthUser(
          userId: 'u', token: 't', refreshToken: 'r',
          capabilities: const ['BARBER', 'CLIENT'],
          primaryRole: 'BARBER',
        );
        expect(u.isBarber, isTrue);
        expect(u.isOwner, isFalse);
        expect(u.isClient, isTrue);
      });

      test('cliente puro solo tiene CLIENT', () {
        final u = _makeClient();
        expect(u.isClient, isTrue);
        expect(u.isOwner, isFalse);
        expect(u.isBarber, isFalse);
      });

      test('hasMultipleRoles true para owner', () {
        final u = _makeOwner(capabilities: ['OWNER', 'CLIENT']);
        expect(u.hasMultipleRoles, isTrue);
      });

      test('hasMultipleRoles false para cliente puro', () {
        final u = _makeClient();
        expect(u.hasMultipleRoles, isFalse);
      });
    });

    group('displayName', () {
      test('prefiere clientProfile.name', () {
        final u = _makeOwner();
        expect(u.displayName, 'Roberto Gómez');
      });

      test('usa tenant.name si no hay clientProfile', () {
        final u = _makeOwner(clientProfile: null);
        expect(u.displayName, "Barba's Club");
      });

      test('usa userId truncado si no hay perfil ni tenant', () {
        final u = AuthUser(
          userId: '12345678-abcd',
          token: 't', refreshToken: 'r',
          capabilities: const ['CLIENT'],
          primaryRole: 'CLIENT',
        );
        expect(u.displayName, '12345678');
      });
    });

    group('initials', () {
      test('dos nombres → dos iniciales', () {
        final u = _makeClient(name: 'Juan Pérez');
        expect(u.initials, 'JP');
      });

      test('un solo nombre → primera inicial', () {
        final u = _makeClient(name: 'Roberto');
        expect(u.initials, 'R');
      });

      test('nombre con tres palabras → primeras dos iniciales', () {
        final u = _makeClient(name: 'Ana María López');
        expect(u.initials, 'AM');
      });

      test('initials en mayúsculas', () {
        final u = _makeClient(name: 'juan pérez');
        expect(u.initials, 'JP');
      });
    });

    group('copyWith — Freezed', () {
      test('copyWith cambia solo el token', () {
        final u = _makeOwner();
        final u2 = u.copyWith(token: 'new-token');
        expect(u2.token, 'new-token');
        expect(u2.userId, u.userId);
        expect(u2.primaryRole, u.primaryRole);
      });

      test('copyWith no muta el original', () {
        final u = _makeOwner();
        final originalToken = u.token;
        u.copyWith(token: 'changed');
        expect(u.token, originalToken); // Inmutable
      });
    });

    group('equality — Freezed', () {
      test('dos AuthUser con mismos datos son iguales', () {
        final u1 = _makeOwner(userId: 'same');
        final u2 = _makeOwner(userId: 'same');
        expect(u1, equals(u2));
      });

      test('AuthUser con distinto userId no son iguales', () {
        final u1 = _makeOwner(userId: 'a');
        final u2 = _makeOwner(userId: 'b');
        expect(u1, isNot(equals(u2)));
      });
    });
  });

  group('AuthMode', () {
    test('loading.routePath es /splash', () {
      expect(AuthMode.loading.routePath, '/splash');
    });

    test('unauthenticated.routePath es /login', () {
      expect(AuthMode.unauthenticated.routePath, '/login');
    });

    test('modeSelection.routePath es /select-mode', () {
      expect(AuthMode.modeSelection.routePath, '/select-mode');
    });

    test('owner.routePath es /owner/home', () {
      expect(AuthMode.owner.routePath, '/owner/home');
    });

    test('barber.routePath es /barber/home', () {
      expect(AuthMode.barber.routePath, '/barber/home');
    });

    test('client.routePath es /client/home', () {
      expect(AuthMode.client.routePath, '/client/home');
    });

    test('loading y unauthenticated no son authenticated', () {
      expect(AuthMode.loading.isAuthenticated, isFalse);
      expect(AuthMode.unauthenticated.isAuthenticated, isFalse);
    });

    test('owner, barber, client son authenticated', () {
      expect(AuthMode.owner.isAuthenticated, isTrue);
      expect(AuthMode.barber.isAuthenticated, isTrue);
      expect(AuthMode.client.isAuthenticated, isTrue);
    });
  });

  group('TenantInfo', () {
    test('FREE plan no es pago', () {
      const t = TenantInfo(id: '1', name: 'Barbería');
      expect(t.isPaidPlan, isFalse);
    });

    test('PRO plan es pago', () {
      const t = TenantInfo(id: '1', name: 'Barbería', plan: 'PRO');
      expect(t.isPaidPlan, isTrue);
    });

    test('STARTER plan es pago', () {
      const t = TenantInfo(id: '1', name: 'Barbería', plan: 'STARTER');
      expect(t.isPaidPlan, isTrue);
    });
  });
}

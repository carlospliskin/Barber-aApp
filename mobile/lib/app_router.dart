import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'features/auth/domain/entities/auth_user.dart';
import 'features/auth/presentation/notifiers/auth_notifier.dart';
import 'features/auth/presentation/screens/login_screen.dart';
import 'features/auth/presentation/screens/mode_selector_screen.dart';
import 'features/home_client/presentation/screens/home_client_screen.dart';
import 'features/home_owner/presentation/screens/home_barber_screen.dart';
import 'features/home_owner/presentation/screens/home_owner_screen.dart';

// Splash placeholder
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});
  @override
  Widget build(BuildContext context) => const Scaffold(
    backgroundColor: Color(0xFF0A0A0B),
    body: Center(child: CircularProgressIndicator(color: Color(0xFFC9A84C))),
  );
}

// ─── Router ───────────────────────────────────────────────────────────────────

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: _AuthListenable(ref),
    redirect: (context, state) {
      final mode = ref.read(authModeProvider);
      final loc = state.matchedLocation;

      final target = mode.routePath;

      // Ya estamos donde debemos → no redirigir
      if (loc == target) return null;

      // Rutas que requieren auth
      const protectedPrefixes = ['/owner', '/barber', '/client', '/select-mode'];
      final isProtected = protectedPrefixes.any((p) => loc.startsWith(p));

      if (mode == AuthMode.unauthenticated && isProtected) return '/login';

      // Redirigir al destino correcto
      if (loc == '/splash' || loc == '/login') return target;

      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/select-mode', builder: (_, __) => const ModeSelectorScreen()),
      GoRoute(path: '/owner/home', builder: (_, __) => const HomeOwnerScreen()),
      GoRoute(path: '/barber/home', builder: (_, __) => const HomeBarberScreen()),
      GoRoute(path: '/client/home', builder: (_, __) => const HomeClientScreen()),
    ],
  );
});

class _AuthListenable extends ChangeNotifier {
  _AuthListenable(Ref ref) {
    ref.listen(authModeProvider, (_, __) => notifyListeners());
  }
}

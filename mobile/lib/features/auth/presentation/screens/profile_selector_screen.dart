import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/widgets/app_components.dart';
import '../providers/auth_provider.dart';

class ProfileSelectorScreen extends ConsumerStatefulWidget {
  const ProfileSelectorScreen({super.key});

  @override
  ConsumerState<ProfileSelectorScreen> createState() =>
      _ProfileSelectorScreenState();
}

class _ProfileSelectorScreenState extends ConsumerState<ProfileSelectorScreen>
    with SingleTickerProviderStateMixin {
  String? _selected; // 'owner' | 'barber' | 'client'
  bool _loading = false;

  late AnimationController _bgCtrl;
  late Animation<double> _bgAnim;

  @override
  void initState() {
    super.initState();
    _bgCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    )..forward();
    _bgAnim = CurvedAnimation(parent: _bgCtrl, curve: Curves.easeOut);
  }

  @override
  void dispose() {
    _bgCtrl.dispose();
    super.dispose();
  }

  Future<void> _selectMode(String mode) async {
    if (_loading) return;
    HapticFeedback.mediumImpact();

    setState(() => _selected = mode);
    await Future.delayed(const Duration(milliseconds: 180));

    setState(() => _loading = true);

    // Si elige modo cliente siendo owner → switchMode en backend
    final user = ref.read(currentUserProvider);
    final currentRole = user?.primaryRole ?? 'CLIENT';

    if (mode == 'client' && currentRole != 'CLIENT') {
      await ref.read(authProvider.notifier).switchMode('CLIENT');
    } else if (mode != 'client' && currentRole == 'CLIENT') {
      final targetRole = mode == 'barber' ? 'BARBER' : 'OWNER';
      await ref.read(authProvider.notifier).switchMode(targetRole);
    }
    // Si el role ya coincide, no hace falta switchMode — el router redirige

    if (mounted) setState(() => _loading = false);
    // El router reacciona al cambio de AuthMode automáticamente
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(currentUserProvider);
    if (user == null) return const SizedBox.shrink();

    final name = user.clientProfile?.name ??
        user.tenant?.name ??
        'Usuario';
    final firstName = name.split(' ').first;
    final isOwner = user.isOwner;
    final isBarber = user.isBarber && !user.isOwner;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
      ),
      child: Scaffold(
        backgroundColor: AppColors.obsidian,
        body: Stack(
          children: [
            _buildBackground(),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.base,
                  vertical: AppSpacing.lg,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: AppSpacing.xl),
                    _buildHeader(firstName, user, isOwner),
                    const SizedBox(height: AppSpacing.xxxl),
                    _buildSectionLabel('¿Cómo quieres entrar hoy?'),
                    const SizedBox(height: AppSpacing.lg),
                    // Tarjetas de modo
                    if (isOwner)
                      _buildModeCard(
                        mode: 'owner',
                        icon: Icons.store_outlined,
                        title: user.tenant?.name ?? 'Mi Barbería',
                        subtitle: 'Gestionar agenda, servicios y clientes',
                        tag: 'ADMINISTRADOR',
                        tagColor: AppColors.gold,
                        detail: user.tenant?.plan ?? 'FREE',
                      ).animate().fadeIn(delay: 200.ms).slideX(begin: -0.1),
                    if (isBarber)
                      _buildModeCard(
                        mode: 'barber',
                        icon: Icons.content_cut_rounded,
                        title: 'Mi agenda',
                        subtitle: 'Ver citas del día y mi horario',
                        tag: 'BARBERO',
                        tagColor: AppColors.silver,
                      ).animate().fadeIn(delay: 200.ms).slideX(begin: -0.1),
                    const SizedBox(height: AppSpacing.md),
                    _buildModeCard(
                      mode: 'client',
                      icon: Icons.search_rounded,
                      title: 'Reservar cita',
                      subtitle: 'Encuentra barberías y agenda tu próxima cita',
                      tag: 'CLIENTE',
                      tagColor: AppColors.confirmed,
                    ).animate().fadeIn(delay: 350.ms).slideX(begin: -0.1),
                    const Spacer(),
                    _buildFooter(),
                  ],
                ),
              ),
            ),
            // Overlay de carga
            if (_loading) _buildLoadingOverlay(),
          ],
        ),
      ),
    );
  }

  // ─── Background ────────────────────────────────────────────────────────────

  Widget _buildBackground() {
    return Positioned.fill(
      child: AnimatedBuilder(
        animation: _bgAnim,
        builder: (_, __) => Stack(
          children: [
            // Fondo base
            Container(color: AppColors.obsidian),
            // Glow superior izquierdo
            Positioned(
              top: -80,
              left: -80,
              child: Opacity(
                opacity: _bgAnim.value * 0.6,
                child: Container(
                  width: 300,
                  height: 300,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        AppColors.gold.withOpacity(0.12),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
            ),
            // Glow inferior derecho
            Positioned(
              bottom: -60,
              right: -60,
              child: Opacity(
                opacity: _bgAnim.value * 0.4,
                child: Container(
                  width: 240,
                  height: 240,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        AppColors.gold.withOpacity(0.08),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Header ────────────────────────────────────────────────────────────────

  Widget _buildHeader(String firstName, AuthUser user, bool isOwner) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Logo pequeño + saludo
        Row(
          children: [
            const AppLogoMark(size: 36),
            const SizedBox(width: AppSpacing.md),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Bienvenido de vuelta',
                  style: AppTypography.overline.copyWith(
                    color: AppColors.ash,
                    letterSpacing: 0.2,
                  ),
                ),
                Text(
                  firstName,
                  style: AppTypography.headingMd.copyWith(
                    color: AppColors.white,
                  ),
                ),
              ],
            ),
            const Spacer(),
            // Avatar
            _buildAvatar(user),
          ],
        )
            .animate()
            .fadeIn(delay: 100.ms)
            .slideY(begin: -0.2),
        const SizedBox(height: AppSpacing.xxl),
        // Título principal
        Text(
          'Tienes',
          style: AppTypography.displayLg.copyWith(
            color: AppColors.silver,
            fontWeight: FontWeight.w400,
          ),
        ).animate().fadeIn(delay: 150.ms).slideX(begin: -0.15),
        Text(
          'acceso múltiple.',
          style: AppTypography.displayLg,
        ).animate().fadeIn(delay: 200.ms).slideX(begin: -0.15),
      ],
    );
  }

  Widget _buildAvatar(AuthUser user) {
    final name = user.clientProfile?.name ?? user.tenant?.name ?? '?';
    final initials = name.trim().split(' ')
        .take(2)
        .map((w) => w.isNotEmpty ? w[0].toUpperCase() : '')
        .join();

    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        gradient: AppColors.goldGradient,
        shape: BoxShape.circle,
        border: Border.all(color: AppColors.gold, width: 1.5),
        boxShadow: AppColors.shadowGold,
      ),
      child: Center(
        child: Text(
          initials,
          style: AppTypography.labelLg.copyWith(
            color: AppColors.obsidian,
            fontWeight: FontWeight.w800,
            fontSize: 15,
          ),
        ),
      ),
    );
  }

  // ─── Section label ─────────────────────────────────────────────────────────

  Widget _buildSectionLabel(String label) {
    return Text(
      label.toUpperCase(),
      style: AppTypography.overline.copyWith(
        color: AppColors.ash,
        letterSpacing: 0.25,
      ),
    ).animate().fadeIn(delay: 300.ms);
  }

  // ─── Tarjeta de modo ───────────────────────────────────────────────────────

  Widget _buildModeCard({
    required String mode,
    required IconData icon,
    required String title,
    required String subtitle,
    required String tag,
    required Color tagColor,
    String? detail,
  }) {
    final isSelected = _selected == mode;

    return GestureDetector(
      onTap: () => _selectMode(mode),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        margin: const EdgeInsets.only(bottom: 0),
        padding: const EdgeInsets.all(AppSpacing.base),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.gold.withOpacity(0.08)
              : AppColors.graphite,
          borderRadius: AppRadius.rLg,
          border: Border.all(
            color: isSelected ? AppColors.gold : AppColors.smoke,
            width: isSelected ? 1.5 : 1.0,
          ),
          boxShadow: isSelected ? AppColors.shadowGold : AppColors.shadowSm,
        ),
        child: Row(
          children: [
            // Icono
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: isSelected
                    ? AppColors.gold.withOpacity(0.15)
                    : AppColors.slate,
                borderRadius: AppRadius.rMd,
                border: Border.all(
                  color: isSelected
                      ? AppColors.gold.withOpacity(0.4)
                      : AppColors.smoke,
                ),
              ),
              child: Icon(
                icon,
                size: 22,
                color: isSelected ? AppColors.gold : AppColors.silver,
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            // Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          title,
                          style: AppTypography.headingSm.copyWith(
                            color: isSelected
                                ? AppColors.white
                                : AppColors.pearl,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      _buildTag(tag, tagColor),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: AppTypography.bodySm.copyWith(
                      color: AppColors.ash,
                    ),
                    maxLines: 2,
                  ),
                  if (detail != null) ...[
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Container(
                          width: 6,
                          height: 6,
                          decoration: const BoxDecoration(
                            color: AppColors.gold,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 5),
                        Text(
                          'Plan $detail activo',
                          style: AppTypography.monoSm.copyWith(
                            color: AppColors.gold,
                            fontSize: 10,
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            // Chevron / Check
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 200),
              child: isSelected
                  ? Container(
                      key: const ValueKey('check'),
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: AppColors.gold,
                        shape: BoxShape.circle,
                        boxShadow: AppColors.shadowGoldBtn,
                      ),
                      child: const Icon(
                        Icons.check_rounded,
                        size: 16,
                        color: AppColors.obsidian,
                      ),
                    )
                  : Container(
                      key: const ValueKey('arrow'),
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: AppColors.slate,
                        shape: BoxShape.circle,
                        border: Border.all(color: AppColors.smoke),
                      ),
                      child: const Icon(
                        Icons.arrow_forward_rounded,
                        size: 14,
                        color: AppColors.ash,
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTag(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 6,
        vertical: 2,
      ),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: AppRadius.rPill,
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Text(
        label,
        style: AppTypography.labelSm.copyWith(
          color: color,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.2,
          fontSize: 9,
        ),
      ),
    );
  }

  // ─── Footer ────────────────────────────────────────────────────────────────

  Widget _buildFooter() {
    return Column(
      children: [
        const AppDivider(),
        const SizedBox(height: AppSpacing.md),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.swap_horiz_rounded,
              size: 14,
              color: AppColors.ash,
            ),
            const SizedBox(width: AppSpacing.sm),
            Text(
              'Puedes cambiar de modo en cualquier momento desde tu perfil',
              style: AppTypography.bodySm.copyWith(
                color: AppColors.ash,
                fontSize: 11,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        GestureDetector(
          onTap: () => ref.read(authProvider.notifier).logout(),
          child: Text(
            'Cerrar sesión',
            style: AppTypography.labelMd.copyWith(
              color: AppColors.error.withOpacity(0.7),
            ),
          ),
        ),
      ],
    ).animate().fadeIn(delay: 500.ms);
  }

  // ─── Loading overlay ───────────────────────────────────────────────────────

  Widget _buildLoadingOverlay() {
    return Positioned.fill(
      child: Container(
        color: AppColors.obsidian.withOpacity(0.6),
        child: Center(
          child: Container(
            padding: const EdgeInsets.all(AppSpacing.xl),
            decoration: BoxDecoration(
              color: AppColors.graphite,
              borderRadius: AppRadius.rLg,
              border: Border.all(color: AppColors.smoke),
              boxShadow: AppColors.shadowLg,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(
                  width: 32,
                  height: 32,
                  child: CircularProgressIndicator(
                    color: AppColors.gold,
                    strokeWidth: 2.5,
                  ),
                ),
                const SizedBox(height: AppSpacing.md),
                Text(
                  'Entrando...',
                  style: AppTypography.bodyMd.copyWith(
                    color: AppColors.silver,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

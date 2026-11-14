import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/widgets/app_components.dart';
import '../notifiers/auth_notifier.dart';

class ModeSelectorScreen extends ConsumerStatefulWidget {
  const ModeSelectorScreen({super.key});

  @override
  ConsumerState<ModeSelectorScreen> createState() =>
      _ModeSelectorScreenState();
}

class _ModeSelectorScreenState extends ConsumerState<ModeSelectorScreen>
    with SingleTickerProviderStateMixin {
  bool _isLoading = false;
  String? _selectedMode;

  late AnimationController _pulseCtrl;
  late Animation<double> _pulse;

  @override
  void initState() {
    super.initState();
    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);

    _pulse = Tween<double>(begin: 0.95, end: 1.0).animate(
      CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseCtrl.dispose();
    super.dispose();
  }

  Future<void> _selectMode(String mode) async {
    if (_isLoading) return;
    HapticFeedback.mediumImpact();

    setState(() {
      _isLoading = true;
      _selectedMode = mode;
    });

    // Llamar switchMode al backend para obtener nuevo JWT
    await ref.read(authNotifierProvider.notifier).switchMode(mode);

    // El router redirige automáticamente cuando cambia el authState
    // No necesitamos navegar manualmente
    if (mounted) {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(currentUserProvider);
    if (user == null) return const SizedBox.shrink();

    final isOwner = user.primaryRole == 'OWNER';
    final isBarber = user.primaryRole == 'BARBER';

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
                    _buildGreeting(user, isOwner),
                    const Spacer(),
                    _buildCards(user, isOwner, isBarber),
                    const Spacer(),
                    _buildFooter(user),
                    const SizedBox(height: AppSpacing.lg),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Background ────────────────────────────────────────────────────────────

  Widget _buildBackground() {
    return Positioned.fill(
      child: Stack(
        children: [
          // Glow dorado arriba
          Positioned(
            top: -80,
            left: -80,
            child: Container(
              width: 300,
              height: 300,
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
          // Glow sutil abajo derecha
          Positioned(
            bottom: -100,
            right: -100,
            child: Container(
              width: 280,
              height: 280,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppColors.gold.withOpacity(0.05),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Greeting ──────────────────────────────────────────────────────────────

  Widget _buildGreeting(AuthUser user, bool isOwner) {
    final name = user.clientProfile?.name ?? user.tenant?.name ?? '';
    final firstName = name.split(' ').first;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const AppLogoMark(size: 36),
            const Spacer(),
            // Avatar
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: AppColors.goldGradient,
                border: Border.all(
                  color: AppColors.gold.withOpacity(0.5),
                  width: 1.5,
                ),
              ),
              child: Center(
                child: Text(
                  firstName.isNotEmpty ? firstName[0].toUpperCase() : 'U',
                  style: AppTypography.headingMd.copyWith(
                    color: AppColors.obsidian,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.xxl),
        Text(
          'Hola, $firstName 👋',
          style: AppTypography.bodySm.copyWith(color: AppColors.silver),
        ).animate().fadeIn(delay: 100.ms),
        const SizedBox(height: AppSpacing.xs),
        Text(
          '¿Cómo\nquieres entrar?',
          style: AppTypography.displayLg,
        ).animate().fadeIn(delay: 150.ms).slideY(begin: 0.2),
        const SizedBox(height: AppSpacing.sm),
        Text(
          isOwner
              ? 'Tienes acceso completo como dueño y como cliente.'
              : 'Puedes gestionar tu agenda o reservar en otra barbería.',
          style: AppTypography.bodyMd.copyWith(color: AppColors.silver),
        ).animate().fadeIn(delay: 250.ms),
      ],
    );
  }

  // ─── Cards de modo ─────────────────────────────────────────────────────────

  Widget _buildCards(AuthUser user, bool isOwner, bool isBarber) {
    return Column(
      children: [
        // Card principal — rol profesional
        _ModeCard(
          icon: isOwner
              ? Icons.storefront_rounded
              : Icons.content_cut_rounded,
          title: isOwner ? 'Modo Barbería' : 'Modo Barbero',
          subtitle: isOwner
              ? 'Gestiona tu negocio, citas y clientes'
              : 'Ve tu agenda y administra tus citas',
          badge: isOwner
              ? user.tenant?.name ?? 'Mi Barbería'
              : 'Tu agenda personal',
          isPrimary: true,
          isLoading: _isLoading && _selectedMode == (isOwner ? 'OWNER' : 'BARBER'),
          onTap: () => _selectMode(isOwner ? 'OWNER' : 'BARBER'),
          highlights: isOwner
              ? ['Agenda del día', 'CRM de clientes', 'Métricas del negocio']
              : ['Mis citas de hoy', 'Horario personal', 'Notificaciones'],
        ).animate().fadeIn(delay: 350.ms).slideY(begin: 0.3),

        const SizedBox(height: AppSpacing.md),

        // Card secundaria — modo cliente
        _ModeCard(
          icon: Icons.person_outline_rounded,
          title: 'Modo Cliente',
          subtitle: 'Reserva citas en cualquier barbería',
          badge: 'Explorar barberías',
          isPrimary: false,
          isLoading: _isLoading && _selectedMode == 'CLIENT',
          onTap: () => _selectMode('CLIENT'),
          highlights: [
            'Buscar barberías',
            'Reservar con depósito',
            'Ver mis citas',
          ],
        ).animate().fadeIn(delay: 450.ms).slideY(begin: 0.3),
      ],
    );
  }

  // ─── Footer ────────────────────────────────────────────────────────────────

  Widget _buildFooter(AuthUser user) {
    return Column(
      children: [
        // Indicador de plan
        if (user.tenant != null) ...[
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md,
              vertical: AppSpacing.sm,
            ),
            decoration: BoxDecoration(
              color: AppColors.graphite,
              borderRadius: AppRadius.rPill,
              border: Border.all(color: AppColors.smoke),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.workspace_premium_rounded,
                  size: 14,
                  color: AppColors.gold,
                ),
                const SizedBox(width: AppSpacing.xs),
                Text(
                  'Plan ${user.tenant!.plan}',
                  style: AppTypography.labelMd.copyWith(
                    color: AppColors.silver,
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Container(
                  width: 1,
                  height: 12,
                  color: AppColors.smoke,
                ),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  user.tenant!.name,
                  style: AppTypography.labelMd.copyWith(
                    color: AppColors.pearl,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ).animate().fadeIn(delay: 550.ms),
          const SizedBox(height: AppSpacing.md),
        ],

        // Cerrar sesión
        GestureDetector(
          onTap: () async {
            HapticFeedback.lightImpact();
            final confirm = await _showLogoutDialog();
            if (confirm == true && mounted) {
              await ref.read(authNotifierProvider.notifier).logout();
            }
          },
          child: Text(
            'Cerrar sesión',
            style: AppTypography.labelMd.copyWith(
              color: AppColors.ash,
            ),
          ),
        ).animate().fadeIn(delay: 600.ms),
      ],
    );
  }

  Future<bool?> _showLogoutDialog() {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text(
          'Cerrar sesión',
          style: AppTypography.headingMd,
        ),
        content: Text(
          '¿Seguro que quieres salir?',
          style: AppTypography.bodyMd,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              'Cancelar',
              style: AppTypography.labelMd.copyWith(color: AppColors.silver),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(
              'Salir',
              style: AppTypography.labelMd.copyWith(
                color: AppColors.error,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── _ModeCard ────────────────────────────────────────────────────────────────

class _ModeCard extends StatefulWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String badge;
  final bool isPrimary;
  final bool isLoading;
  final VoidCallback onTap;
  final List<String> highlights;

  const _ModeCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.badge,
    required this.isPrimary,
    required this.isLoading,
    required this.onTap,
    required this.highlights,
  });

  @override
  State<_ModeCard> createState() => _ModeCardState();
}

class _ModeCardState extends State<_ModeCard>
    with SingleTickerProviderStateMixin {
  late AnimationController _pressCtrl;
  late Animation<double> _scale;
  bool _isPressed = false;

  @override
  void initState() {
    super.initState();
    _pressCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 120),
    );
    _scale = Tween<double>(begin: 1.0, end: 0.97).animate(
      CurvedAnimation(parent: _pressCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pressCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) {
        setState(() => _isPressed = true);
        _pressCtrl.forward();
      },
      onTapUp: (_) {
        setState(() => _isPressed = false);
        _pressCtrl.reverse();
        widget.onTap();
      },
      onTapCancel: () {
        setState(() => _isPressed = false);
        _pressCtrl.reverse();
      },
      child: ScaleTransition(
        scale: _scale,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.all(AppSpacing.base + 4),
          decoration: BoxDecoration(
            color: widget.isPrimary
                ? AppColors.graphite
                : AppColors.carbon,
            borderRadius: AppRadius.rCardLg,
            border: Border.all(
              color: widget.isPrimary
                  ? (_isPressed
                      ? AppColors.gold
                      : AppColors.gold.withOpacity(0.35))
                  : AppColors.smoke,
              width: widget.isPrimary ? 1.5 : 1.0,
            ),
            boxShadow: widget.isPrimary
                ? [
                    BoxShadow(
                      color: AppColors.gold.withOpacity(_isPressed ? 0.2 : 0.1),
                      blurRadius: _isPressed ? 32 : 20,
                      spreadRadius: 0,
                    ),
                  ]
                : null,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                children: [
                  // Icono
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      gradient: widget.isPrimary
                          ? AppColors.goldGradient
                          : null,
                      color: widget.isPrimary ? null : AppColors.graphite,
                      borderRadius: AppRadius.rMd,
                      border: widget.isPrimary
                          ? null
                          : Border.all(color: AppColors.smoke),
                    ),
                    child: widget.isLoading
                        ? const Center(
                            child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppColors.obsidian,
                              ),
                            ),
                          )
                        : Icon(
                            widget.icon,
                            size: 24,
                            color: widget.isPrimary
                                ? AppColors.obsidian
                                : AppColors.silver,
                          ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  // Title + badge
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.title,
                          style: AppTypography.headingMd.copyWith(
                            color: widget.isPrimary
                                ? AppColors.white
                                : AppColors.silver,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpacing.sm,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: widget.isPrimary
                                ? AppColors.goldSubtle
                                : AppColors.graphite,
                            borderRadius: AppRadius.rPill,
                            border: Border.all(
                              color: widget.isPrimary
                                  ? AppColors.gold.withOpacity(0.3)
                                  : AppColors.smoke,
                            ),
                          ),
                          child: Text(
                            widget.badge,
                            style: AppTypography.monoSm.copyWith(
                              color: widget.isPrimary
                                  ? AppColors.gold
                                  : AppColors.ash,
                              fontSize: 10,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                  // Flecha
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    transform: Matrix4.translationValues(
                      _isPressed ? 4 : 0,
                      0,
                      0,
                    ),
                    child: Icon(
                      Icons.arrow_forward_ios_rounded,
                      size: 14,
                      color: widget.isPrimary ? AppColors.gold : AppColors.ash,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),

              // Subtitle
              Text(
                widget.subtitle,
                style: AppTypography.bodyMd.copyWith(
                  color: widget.isPrimary
                      ? AppColors.silver
                      : AppColors.ash,
                ),
              ),
              const SizedBox(height: AppSpacing.md),

              // Divider
              Container(height: 1, color: AppColors.smoke),
              const SizedBox(height: AppSpacing.md),

              // Highlights
              Row(
                children: widget.highlights.map((h) {
                  return Expanded(
                    child: Row(
                      children: [
                        Container(
                          width: 4,
                          height: 4,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: widget.isPrimary
                                ? AppColors.gold
                                : AppColors.ash,
                          ),
                        ),
                        const SizedBox(width: 5),
                        Flexible(
                          child: Text(
                            h,
                            style: AppTypography.labelSm.copyWith(
                              color: widget.isPrimary
                                  ? AppColors.pearl
                                  : AppColors.ash,
                              fontSize: 10,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

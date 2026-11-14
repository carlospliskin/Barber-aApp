import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../notifiers/home_owner_notifier.dart';
import 'manage_loyalty_screen.dart';
import 'manage_schedule_screen.dart';
import 'manage_services_screen.dart';

class ConfigScreen extends ConsumerWidget {
  const ConfigScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final tenant = user?.tenant;

    return Scaffold(
      backgroundColor: AppColors.obsidian,
      appBar: AppBar(
        backgroundColor: AppColors.obsidian,
        title: Text('Configuración', style: AppTypography.headingMd),
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: const Icon(Icons.arrow_back_ios_rounded,
            size: 18, color: AppColors.white),
        ),
      ),
      body: ListView(
        padding: AppSpacing.screen,
        children: [

          // ── Perfil de la barbería ──────────────────────────────────────
          _SectionHeader('MI BARBERÍA'),
          _ConfigCard(
            icon: Icons.storefront_outlined,
            title: tenant?.name ?? 'Mi Barbería',
            subtitle: 'Plan ${tenant?.plan ?? 'FREE'} · ${tenant?.timezone ?? ''}',
            onTap: () => _showEditBarberia(context, ref),
          ),

          const SizedBox(height: AppSpacing.lg),

          // ── Gestión ───────────────────────────────────────────────────
          _SectionHeader('GESTIÓN'),
          _ConfigCard(
            icon: Icons.content_cut_rounded,
            title: 'Servicios',
            subtitle: 'Agregar, editar y desactivar servicios',
            onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const ManageServicesScreen())),
          ),
          _ConfigCard(
            icon: Icons.schedule_rounded,
            title: 'Horarios',
            subtitle: 'Configurar horario por barbero',
            onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const ManageScheduleScreen())),
          ),
          _ConfigCard(
            icon: Icons.card_giftcard_rounded,
            title: 'Fidelización',
            subtitle: 'Programa de lealtad y recompensas',
            onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const ManageLoyaltyScreen())),
          ),

          const SizedBox(height: AppSpacing.lg),

          // ── Plan ──────────────────────────────────────────────────────
          _SectionHeader('PLAN'),
          GoldGlowContainer(
            child: Row(
              children: [
                Container(
                  width: 40, height: 40,
                  decoration: BoxDecoration(
                    color: AppColors.goldSubtle,
                    borderRadius: AppRadius.rSm),
                  child: const Icon(Icons.workspace_premium_rounded,
                    color: AppColors.gold, size: 20),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Plan ${tenant?.plan ?? 'FREE'}',
                        style: AppTypography.headingSm),
                      Text(
                        tenant?.plan == 'FREE'
                            ? 'Sin comisiones · Funciones básicas'
                            : 'Acceso completo · Soporte prioritario',
                        style: AppTypography.bodySm),
                    ],
                  ),
                ),
                if (tenant?.plan == 'FREE')
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.sm, vertical: 4),
                    decoration: BoxDecoration(
                      gradient: AppColors.goldGradient,
                      borderRadius: AppRadius.rPill),
                    child: Text('Mejorar',
                      style: AppTypography.labelSm.copyWith(
                        color: AppColors.obsidian,
                        fontWeight: FontWeight.w700)),
                  ),
              ],
            ),
          ),

          const SizedBox(height: AppSpacing.lg),

          // ── Cuenta ────────────────────────────────────────────────────
          _SectionHeader('CUENTA'),
          _ConfigCard(
            icon: Icons.person_outline_rounded,
            title: user?.displayName ?? 'Mi perfil',
            subtitle: 'Datos personales y contraseña',
            onTap: () {/* TODO: pantalla de perfil */},
          ),

          const SizedBox(height: AppSpacing.lg),

          // ── Cerrar sesión ─────────────────────────────────────────────
          AppButton(
            label: 'Cerrar sesión',
            variant: AppButtonVariant.danger,
            onTap: () => _showLogoutConfirm(context, ref),
          ),

          const SizedBox(height: AppSpacing.xl),

          // Versión
          Center(
            child: Text(
              'Barbería SaaS v1.0.0',
              style: AppTypography.overline.copyWith(
                color: AppColors.ash, fontSize: 9),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
        ],
      ),
    );
  }

  Future<void> _showEditBarberia(BuildContext context, WidgetRef ref) async {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ProviderScope(
        parent: ProviderScope.containerOf(context),
        child: const _EditBarberiaSheet(),
      ),
    );
  }

  Future<void> _showLogoutConfirm(BuildContext context, WidgetRef ref) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cerrar sesión', style: AppTypography.headingMd),
        content: Text('¿Seguro que quieres salir?',
          style: AppTypography.bodyMd),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Salir', style: AppTypography.labelMd.copyWith(
              color: AppColors.error, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm == true && context.mounted) {
      ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

// ─── _EditBarberiaSheet ───────────────────────────────────────────────────────

class _EditBarberiaSheet extends ConsumerStatefulWidget {
  const _EditBarberiaSheet();

  @override
  ConsumerState<_EditBarberiaSheet> createState() =>
      _EditBarberiaSheetState();
}

class _EditBarberiaSheetState extends ConsumerState<_EditBarberiaSheet> {
  late final TextEditingController _nameCtrl;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    final user = ref.read(currentUserProvider);
    _nameCtrl = TextEditingController(text: user?.tenant?.name ?? '');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rBottomSheet),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Datos de la barbería',
                  style: AppTypography.headingMd),
                const SizedBox(height: AppSpacing.xl),
                AppTextField(
                  label: 'Nombre de la barbería',
                  controller: _nameCtrl,
                  textInputAction: TextInputAction.done,
                  prefixIcon: const Icon(Icons.storefront_outlined,
                    size: 18, color: AppColors.ash),
                ),
                const SizedBox(height: AppSpacing.xl),
                AppButton(
                  label: 'Guardar cambios',
                  loading: _isLoading,
                  onTap: _save,
                ),
                const SizedBox(height: AppSpacing.md),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    if (_nameCtrl.text.trim().isEmpty) return;
    setState(() => _isLoading = true);
    HapticFeedback.mediumImpact();
    // TODO: conectar con UpdateTenantUseCase cuando el backend lo exponga
    await Future.delayed(const Duration(milliseconds: 600));
    if (mounted) {
      setState(() => _isLoading = false);
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Datos actualizados',
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.confirmed,
        behavior: SnackBarBehavior.floating,
      ));
    }
  }
}

// ─── Widgets internos ────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Text(title, style: AppTypography.overline.copyWith(
        color: AppColors.ash)),
    );
  }
}

class _ConfigCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _ConfigCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        onTap();
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke)),
        child: Row(
          children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                color: AppColors.slate, borderRadius: AppRadius.rSm),
              child: Icon(icon, size: 18, color: AppColors.silver),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: AppTypography.labelLg.copyWith(
                    color: AppColors.white)),
                  Text(subtitle, style: AppTypography.bodySm.copyWith(
                    fontSize: 11)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded,
              size: 18, color: AppColors.ash),
          ],
        ),
      ),
    );
  }
}

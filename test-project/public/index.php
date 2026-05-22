<?php

require_once __DIR__ . '/../vendor/autoload.php';

use App\Model\Order;
use App\Model\User;
use App\Service\CoreConfigurator;
use App\Service\EmailService;
use App\Service\Raketa;
use App\Service\UserService;

$user = new User(42, 'user@example.com');
echo $user->getEmail() . PHP_EOL;

$emailService = new EmailService('noreply', 'noreply@example.com');
echo $emailService->getName() . PHP_EOL;
echo $emailService->execute('Welcome!') . PHP_EOL;

$userService = new UserService(7);
echo $userService->execute('Hello!') . PHP_EOL;
print_r($userService->getLogs());

$order = new Order(1, 'Test', 99.99);
echo $order->process($emailService) . PHP_EOL;
echo $order->process($userService) . PHP_EOL;

$raketa = new Raketa('MyApp');
$raketa->doWork();
print_r($raketa->getMetrics());

$configurator = new CoreConfigurator('/etc/config.ini');
$configurator->configure();
print_r($configurator->getMetrics());
